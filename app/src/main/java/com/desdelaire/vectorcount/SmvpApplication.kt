package com.desdelaire.vectorcount

import android.app.Application
import android.content.Context

class SmvpApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        com.cySdkyc.clx.Helper.install(this)
    }
}
