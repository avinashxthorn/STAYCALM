package com.staycalm.focus

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Local UI fixtures never log in to or write to the owner's production Firebase project. */
class AccountTestApplication : StaycalmApplication() {
    var identity: String? = "ui-test"
    override fun accountUid() = identity
    override fun verifiedAccountUid() = identity
}

class AccountTestRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, name: String, context: Context): Application =
        super.newApplication(loader, AccountTestApplication::class.java.name, context)
}
