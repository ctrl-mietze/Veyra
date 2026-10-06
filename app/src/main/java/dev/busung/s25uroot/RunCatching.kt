package ctrl.mietze.veyraroot

import kotlinx.coroutines.CancellationException

/**
 * `runCatching`, except a cancelled call stays cancelled.
 *
 * `runCatching` catches *everything*, and the coroutine machinery unwinds a cancelled job by throwing
 * [CancellationException] - so an ordinary `runCatching` turns a cancellation into a `Result.failure`
 * and the `.onFailure` beside it runs. What that branch then does is the problem, because it does not
 * know it is reporting on work nobody is waiting for any more:
 *
 * - a read the user backed out of is filed in the shared app log as a transport that did not answer;
 * - a screen that has already moved on records an error about the thing it moved away from;
 * - a job that was cancelled mid-write files the interruption as the failure of the write.
 *
 * Rethrowing is not this function being fastidious, it is what cancellation *means*: the machinery is
 * unwinding the coroutine, and a branch that swallows the exception leaves it believing the work it
 * cancelled is still running. Use this wherever the failure branch does something observable - a log
 * line, a screen's error state, a store - and leave the plain `runCatching` for blocks whose result is
 * only ever read locally, where a cancellation arriving as a failure costs nothing.
 *
 * A `TimeoutCancellationException` from a caller's own [kotlinx.coroutines.withTimeout] is caught by
 * this too, and on purpose: it is a cancellation, and the timeout belongs to the caller that asked for
 * it rather than to the block that happened to be running when it expired.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
