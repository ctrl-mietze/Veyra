#!/system/bin/sh
#
# Marks the block devices an image could be written to read-only, for as long as this boot lasts.
#
# This runs with bootstrap root, which is the window where an unfamiliar payload is executing and the
# only root on the device is temporary. Setting these devices read-only at that point is what keeps a
# write to a boot or vbmeta partition from turning a rooted phone into one that only download mode can
# see. It is opt-in because it also stops the writes a person may legitimately intend - flashing from
# the phone, or a kernel installer that writes a partition directly - and nothing here can tell the
# two apart.
#
# `blockdev --setro` sets the flag on the kernel's block device, so it lasts until reboot and applies
# to every writer. Download-mode flashing is unaffected: that runs in the bootloader, not this kernel.
#
# The last line is the number of devices that were set, which is the app's whole interface to this.
partitions="boot dtbo init_boot vendor_boot"
# Dynamic partitions
partitions="${partitions} super"
# CSC
partitions="${partitions} optics prism"
# AVB
partitions="${partitions} vbmeta"

count=0
for p in ${partitions}; do
  [ -e "/dev/block/by-name/${p}" ] && blockdev --setro "/dev/block/by-name/${p}" 2>/dev/null && count=$((count+1))
  # Include A/B slots
  [ -e "/dev/block/by-name/${p}_a" ] && blockdev --setro "/dev/block/by-name/${p}_a" 2>/dev/null && count=$((count+1))
  [ -e "/dev/block/by-name/${p}_b" ] && blockdev --setro "/dev/block/by-name/${p}_b" 2>/dev/null && count=$((count+1))
done
echo ${count}
