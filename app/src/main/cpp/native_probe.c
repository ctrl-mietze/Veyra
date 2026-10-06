#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <linux/bpf.h>
#include <linux/perf_event.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>

#define KSU_INSTALL_MAGIC1 ((uint32_t)0xDEADBEEF)
#define KSU_INSTALL_MAGIC2 ((uint32_t)0xCAFEBABE)

struct ksu_get_info_cmd {
  uint32_t version;
  uint32_t flags;
  uint32_t features;
  uint32_t uapi_version;
};

struct ksu_get_info_legacy_cmd {
  uint32_t version;
  uint32_t flags;
  uint32_t features;
};

#define KSU_IOCTL_GET_INFO _IOR('K', 2, struct ksu_get_info_cmd)
#define KSU_IOCTL_GET_INFO_LEGACY _IOC(_IOC_READ, 'K', 2, 0)

/*
 * Probe KernelSU's real supercall/control path without risking the Android app process.
 *
 * Samsung seccomp may SIGSYS a reboot supercall from an ordinary app domain. KernelSU's own
 * userspace installs the driver fd through that syscall and handles SIGSYS; doing it in a short
 * child is even safer for a status probe: a blocked syscall can kill the child and the parent still
 * returns an honest 'no proof' instead of taking Veyra down with it.
 */
static uint32_t probe_kernelsu_driver_child(void) {
  int driver_fd = -1;
  (void)syscall(SYS_reboot, KSU_INSTALL_MAGIC1, KSU_INSTALL_MAGIC2, 0,
                &driver_fd);
  if (driver_fd < 0) {
    return 0;
  }

  struct ksu_get_info_cmd info;
  memset(&info, 0, sizeof(info));
  if (ioctl(driver_fd, KSU_IOCTL_GET_INFO, &info) == 0 && info.version > 0) {
    close(driver_fd);
    return info.version;
  }

  struct ksu_get_info_legacy_cmd legacy;
  memset(&legacy, 0, sizeof(legacy));
  if (ioctl(driver_fd, KSU_IOCTL_GET_INFO_LEGACY, &legacy) == 0 &&
      legacy.version > 0) {
    close(driver_fd);
    return legacy.version;
  }

  close(driver_fd);
  return 0;
}

static int probe_kernelsu_driver_isolated(void) {
  int result_pipe[2];
  if (pipe(result_pipe) != 0) {
    return 0;
  }

  pid_t child = fork();
  if (child < 0) {
    close(result_pipe[0]);
    close(result_pipe[1]);
    return 0;
  }

  if (child == 0) {
    close(result_pipe[0]);
    uint32_t version = probe_kernelsu_driver_child();
    if (version > 0) {
      (void)write(result_pipe[1], &version, sizeof(version));
    }
    close(result_pipe[1]);
    _exit(version > 0 ? 0 : 1);
  }

  close(result_pipe[1]);
  uint32_t version = 0;
  struct pollfd pfd = {
      .fd = result_pipe[0],
      .events = POLLIN | POLLHUP,
      .revents = 0,
  };
  int poll_result = poll(&pfd, 1, 750);
  if (poll_result > 0 && (pfd.revents & POLLIN) != 0) {
    ssize_t count = read(result_pipe[0], &version, sizeof(version));
    if (count != (ssize_t)sizeof(version)) {
      version = 0;
    }
  }
  close(result_pipe[0]);

  int status = 0;
  pid_t waited = waitpid(child, &status, WNOHANG);
  if (waited == 0) {
    (void)kill(child, SIGKILL);
    (void)waitpid(child, &status, 0);
  }
  return version > 0;
}

static void append_probe(char *output, size_t output_size, const char *name,
                         const char *path, int flags) {
  errno = 0;
  int fd = open(path, flags | O_CLOEXEC);
  int saved_errno = errno;
  if (fd >= 0) {
    close(fd);
  }

  size_t used = strlen(output);
  if (used >= output_size) {
    return;
  }
  snprintf(output + used, output_size - used, "%s=%s errno=%d\n", name,
           fd >= 0 ? "ok" : "denied", saved_errno);
}

static int read_line(const char *path, char *output, size_t output_size) {
  int fd = open(path, O_RDONLY | O_CLOEXEC);
  if (fd < 0) {
    return 0;
  }
  ssize_t count = read(fd, output, output_size - 1);
  close(fd);
  if (count <= 0) {
    return 0;
  }
  output[count] = '\0';
  output[strcspn(output, "\r\n")] = '\0';
  return 1;
}

#if defined(__aarch64__)
static inline uint64_t read_virtual_counter(void) {
  uint64_t value;
  __asm__ volatile("isb\n\tmrs %0, cntvct_el0\n\tisb" : "=r"(value));
  return value;
}

static uint64_t measure_prefetch(uintptr_t address) {
  __asm__ volatile("dsb sy\n\tisb" ::: "memory");
  uint64_t started = read_virtual_counter();
  for (int i = 0; i < 1024; ++i) {
    __asm__ volatile("prfm pldl1keep, [%0]" : : "r"(address) : "memory");
  }
  __asm__ volatile("dsb sy\n\tisb" ::: "memory");
  return read_virtual_counter() - started;
}

static uint64_t measure_syscall_register(uintptr_t address) {
  register uint64_t x0 __asm__("x0") = address;
  register uint64_t x8 __asm__("x8") = SYS_getpid;
  __asm__ volatile("dsb sy\n\tisb" ::: "memory");
  uint64_t started = read_virtual_counter();
  for (int i = 0; i < 32; ++i) {
    x0 = address;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8) : "memory", "cc");
  }
  __asm__ volatile("isb" ::: "memory");
  return read_virtual_counter() - started;
}

static int compare_u64(const void *left, const void *right) {
  uint64_t a = *(const uint64_t *)left;
  uint64_t b = *(const uint64_t *)right;
  return (a > b) - (a < b);
}

static uint64_t filtered_measurement(uintptr_t address,
                                     uint64_t (*measure)(uintptr_t)) {
  uint64_t samples[128];
  for (size_t i = 0; i < sizeof(samples) / sizeof(samples[0]); ++i) {
    samples[i] = measure(address);
  }
  qsort(samples, sizeof(samples) / sizeof(samples[0]), sizeof(samples[0]),
        compare_u64);
  uint64_t sum = 0;
  for (size_t i = 0; i < 16; ++i) {
    sum += samples[i];
  }
  return sum / 16;
}

static void append_kaslr_timing_probe(char *output, size_t output_size) {
  for (uintptr_t offset = 0; offset <= 0x1f0000; offset += 0x10000) {
    uintptr_t address = 0xffffffc080000000ULL + offset;
    uint64_t prefetch = filtered_measurement(address, measure_prefetch);
    uint64_t syscall_register =
        filtered_measurement(address, measure_syscall_register);
    size_t used = strlen(output);
    if (used >= output_size) {
      return;
    }
    snprintf(output + used, output_size - used,
             "kaslr_%02zx prefetch=%llu syscall=%llu\n", offset >> 16,
             (unsigned long long)prefetch,
             (unsigned long long)syscall_register);
  }
}
#endif

JNIEXPORT jstring JNICALL
Java_dev_busung_s25uroot_NativeProbe_run(JNIEnv *env, jobject thiz) {
  (void)thiz;
  char output[8192] = {0};
  char context[256] = "unknown";
  int context_fd = open("/proc/self/attr/current", O_RDONLY | O_CLOEXEC);
  if (context_fd >= 0) {
    ssize_t count = read(context_fd, context, sizeof(context) - 1);
    close(context_fd);
    if (count > 0) {
      context[count] = '\0';
      context[strcspn(context, "\r\n")] = '\0';
    }
  }

  snprintf(output, sizeof(output),
           "uid=%u euid=%u gid=%u egid=%u\ncontext=%s\npage_size=%ld\n",
           getuid(), geteuid(), getgid(), getegid(), context,
           sysconf(_SC_PAGESIZE));
  append_probe(output, sizeof(output), "tracefs_control",
               "/sys/kernel/tracing/tracing_on", O_RDWR);
  append_probe(output, sizeof(output), "tracefs_event",
               "/sys/kernel/tracing/events/workqueue/workqueue_execute_start/enable",
               O_RDWR);
  append_probe(output, sizeof(output), "tracefs_pipe",
               "/sys/kernel/tracing/per_cpu/cpu0/trace_pipe_raw", O_RDONLY);
  append_probe(output, sizeof(output), "ashmem", "/dev/ashmem", O_RDWR);
  char boot_id[64] = {0};
  if (read_line("/proc/sys/kernel/random/boot_id", boot_id,
                sizeof(boot_id))) {
    char ashmem_libcutils[128];
    snprintf(ashmem_libcutils, sizeof(ashmem_libcutils), "/dev/ashmem%s",
             boot_id);
    append_probe(output, sizeof(output), "ashmem_libcutils",
                 ashmem_libcutils, O_RDWR);
  }
  append_probe(output, sizeof(output), "slabinfo", "/proc/slabinfo", O_RDONLY);
  append_probe(output, sizeof(output), "boot_id",
               "/proc/sys/kernel/random/boot_id", O_RDONLY);
  append_probe(output, sizeof(output), "proc_self_mem", "/proc/self/mem",
               O_RDONLY);

  struct perf_event_attr perf_attr;
  memset(&perf_attr, 0, sizeof(perf_attr));
  perf_attr.type = PERF_TYPE_SOFTWARE;
  perf_attr.size = sizeof(perf_attr);
  perf_attr.config = PERF_COUNT_SW_CPU_CLOCK;
  perf_attr.sample_period = 100000;
  perf_attr.sample_type = PERF_SAMPLE_IP | PERF_SAMPLE_CALLCHAIN;
  perf_attr.disabled = 1;
  errno = 0;
  int perf_fd = (int)syscall(SYS_perf_event_open, &perf_attr, 0, -1, -1, 0);
  int perf_errno = errno;
  if (perf_fd >= 0) {
    close(perf_fd);
  }
  size_t perf_used = strlen(output);
  if (perf_used < sizeof(output)) {
    snprintf(output + perf_used, sizeof(output) - perf_used,
             "perf_event_open=%s errno=%d\n",
             perf_fd >= 0 ? "ok" : "denied", perf_errno);
  }

  union bpf_attr bpf_attr;
  memset(&bpf_attr, 0, sizeof(bpf_attr));
  bpf_attr.map_type = BPF_MAP_TYPE_ARRAY;
  bpf_attr.key_size = sizeof(uint32_t);
  bpf_attr.value_size = sizeof(uint64_t);
  bpf_attr.max_entries = 1;
  errno = 0;
  int bpf_fd = (int)syscall(SYS_bpf, BPF_MAP_CREATE, &bpf_attr,
                            sizeof(bpf_attr));
  int bpf_errno = errno;
  if (bpf_fd >= 0) {
    close(bpf_fd);
  }
  size_t bpf_used = strlen(output);
  if (bpf_used < sizeof(output)) {
    snprintf(output + bpf_used, sizeof(output) - bpf_used,
             "bpf_map_create=%s errno=%d\n",
             bpf_fd >= 0 ? "ok" : "denied", bpf_errno);
  }

  int tee_source[2];
  int tee_target[2];
  int tee_result = -1;
  int tee_errno = 0;
  if (pipe(tee_source) == 0 && pipe(tee_target) == 0) {
    char marker = 'T';
    if (write(tee_source[1], &marker, sizeof(marker)) == sizeof(marker)) {
      errno = 0;
      tee_result = (int)syscall(SYS_tee, tee_source[0], tee_target[1],
                                sizeof(marker), 0);
      tee_errno = errno;
    }
    close(tee_source[0]);
    close(tee_source[1]);
    close(tee_target[0]);
    close(tee_target[1]);
  }
  size_t tee_used = strlen(output);
  if (tee_used < sizeof(output)) {
    snprintf(output + tee_used, sizeof(output) - tee_used,
             "tee=%d errno=%d\n", tee_result, tee_errno);
  }

  long page_size = sysconf(_SC_PAGESIZE);
  unsigned char *page = mmap(NULL, (size_t)page_size, PROT_READ | PROT_WRITE,
                             MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (page != MAP_FAILED) {
    page[0] = 1;
    int pagemap_fd = open("/proc/self/pagemap", O_RDONLY | O_CLOEXEC);
    uint64_t entry = 0;
    ssize_t count = -1;
    if (pagemap_fd >= 0) {
      off_t offset = (off_t)(((uintptr_t)page / (uintptr_t)page_size) * 8);
      count = pread(pagemap_fd, &entry, sizeof(entry), offset);
      close(pagemap_fd);
    }
    size_t used = strlen(output);
    if (used < sizeof(output)) {
      snprintf(output + used, sizeof(output) - used,
               "pagemap=%s read=%zd present=%llu pfn=%llx errno=%d\n",
               pagemap_fd >= 0 ? "ok" : "denied", count,
               (unsigned long long)((entry >> 63) & 1),
               (unsigned long long)(entry & ((1ULL << 55) - 1)), errno);
    }
    munmap(page, (size_t)page_size);
  }

#if defined(__aarch64__)
  append_kaslr_timing_probe(output, sizeof(output));
#endif

  return (*env)->NewStringUTF(env, output);
}

static int kernelsu_visible_in_files(void) {
  if (access("/sys/module/kernelsu", F_OK) == 0) {
    return 1;
  }

  int fd = open("/proc/modules", O_RDONLY | O_CLOEXEC);
  if (fd < 0) {
    return 0;
  }

  char modules[65536];
  ssize_t count = read(fd, modules, sizeof(modules) - 1);
  close(fd);
  if (count <= 0) {
    return 0;
  }
  modules[count] = '\0';

  const char *line = modules;
  while (line != NULL && *line != '\0') {
    if (strncmp(line, "kernelsu ", sizeof("kernelsu ") - 1) == 0) {
      return 1;
    }
    line = strchr(line, '\n');
    if (line != NULL) {
      ++line;
    }
  }
  return 0;
}

JNIEXPORT jboolean JNICALL
Java_dev_busung_s25uroot_NativeProbe_isKernelSuVisibleQuick(JNIEnv *env,
                                                             jobject thiz) {
  (void)env;
  (void)thiz;
  return kernelsu_visible_in_files() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_busung_s25uroot_NativeProbe_isKernelSuActive(JNIEnv *env,
                                                       jobject thiz) {
  (void)env;
  (void)thiz;

  // Prefer KernelSU's actual userspace control plane. The child isolation above means a seccomp
  // refusal cannot kill Veyra itself, while a positive ioctl is the same class of proof the manager
  // uses instead of relying on sysfs/proc visibility.
  if (probe_kernelsu_driver_isolated()) {
    return JNI_TRUE;
  }
  return kernelsu_visible_in_files() ? JNI_TRUE : JNI_FALSE;
}
