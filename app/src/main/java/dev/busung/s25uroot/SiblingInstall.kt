package ctrl.mietze.veyraroot

import android.content.Context

/**
 * Whether the app this fork came from is installed on this device.
 *
 * One question, asked for one reason: `/data/local/tmp` is not per-app, and five of the paths in
 * [StagedResidue] are the same for both installs because they belong to the payload rather than to
 * either app. A sweep is the only thing on this device that deletes by name, so it is the only thing
 * that can take a file out from under a workload that is using it - and with this install's own
 * processes it already stands down (see [RunInFlight]); with the other install's it cannot see that far,
 * so it asks instead whether there is anything to be careful of.
 *
 * False is the ordinary answer, and the one that costs nothing: on a device with only this app installed
 * the sweep removes those names like any other, which is what keeps a run's five-megabyte daemon copy
 * from sitting in the directory for a detector to find. True is the case the shared list exists for, and
 * it is a rare one - two installs of the same app, one of them mid-run, at the moment the other is
 * opened.
 *
 * Read from the package manager rather than remembered, so uninstalling the other app restores the
 * wider sweep without anything having to notice that it happened.
 */
internal object SiblingInstall {

    /**
     * The package id every build of Root My Galaxy has carried, this fork's included before it moved
     * to one of its own.
     *
     * It is the id the other install writes its staged files under, and it is the only one that can be
     * named: a fork that has moved its own id is exactly as invisible to this check as this app is to
     * it, which is why the sweep's answer is a smaller list rather than a clever one.
     */
    const val PACKAGE = "ctrl.mietze.veyraroot"

    fun isPresent(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
}
