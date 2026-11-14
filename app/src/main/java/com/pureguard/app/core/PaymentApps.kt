package com.pureguard.app.core

import android.content.Context
import com.pureguard.app.guard.Browsers

/**
 * Banking, UPI and wallet apps. PureGuard never inspects these and never
 * routes them through its optional VPN, so they behave exactly as if
 * PureGuard's optional layers were not there.
 */
object PaymentApps {
    private val KNOWN = setOf(
        "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp", "com.dreamplug.androidapp", "in.amazon.mShop.android.shopping",
        "com.mobikwik_new", "com.freecharge.android", "com.sbi.lotusintouch", "com.sbi.upi",
        "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.axis.mobile",
        "com.msf.kbank.mobile", "com.samsung.android.spay", "com.samsung.android.spaylite",
        "com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile",
        "com.myairtelapp", "com.jio.myjio", "com.whatsapp.w4b", "com.bharatpe.app",
        "com.zerodha.kite3", "com.nextbillion.groww", "in.upstox.app",
        "com.oneplus.pay", "com.finshell.wallet", "com.coloros.wallet",
    )

    private val HINT = Regex(
        "bank|upi|bhim|npci|paytm|phonepe|gpay|paisa|wallet|mobikwik|freecharge|payzapp|" +
            "razorpay|yono|hdfc|icici|kotak|idfc|indusind|canara|federal|zerodha|groww|" +
            "upstox|paypal|venmo|revolut|payment|\\.pay\\.|\\.pay$|pay\\.|finance|fintech",
    )

    fun isPayment(pkg: String): Boolean {
        if (pkg in KNOWN) return true
        if (Browsers.isBrowser(pkg)) return false
        return HINT.containsMatchIn(pkg.lowercase())
    }

    fun installed(context: Context): List<String> = try {
        context.packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .filter { isPayment(it) }
    } catch (_: Exception) {
        emptyList()
    }
}
