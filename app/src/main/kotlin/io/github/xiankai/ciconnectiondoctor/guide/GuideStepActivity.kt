package io.github.xiankai.ciconnectiondoctor.guide

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.xiankai.ciconnectiondoctor.MainActivity
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep
import io.github.xiankai.ciconnectiondoctor.guard.TargetStore

class GuideStepActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val step = intent.getStringExtra(EXTRA_STEP)?.let(GuideStep::valueOf)
        val address = TargetStore(this).address
        if (step != null && address != null) {
            openFirstAvailable(stepTargets(step, address, MainActivity.intent(this, showSetup = true)))
        }
        finish()
    }

    private fun openFirstAvailable(targets: List<Intent>) {
        targets.firstOrNull { target ->
            runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { if (it !is ActivityNotFoundException && it !is SecurityException) throw it }
                .isSuccess
        }
    }

    companion object {
        private const val EXTRA_STEP = "step"

        fun intent(context: Context, step: GuideStep): Intent =
            Intent(context, GuideStepActivity::class.java)
                .putExtra(EXTRA_STEP, step.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
