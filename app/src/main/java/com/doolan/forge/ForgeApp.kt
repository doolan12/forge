package com.doolan.forge

import android.app.Application
import com.doolan.forge.data.ForgeDatabase
import com.doolan.forge.data.ProfileStore
import com.doolan.forge.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ForgeApp : Application() {
    lateinit var repo: Repository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, ForgeDatabase.build(this), ProfileStore(this))
        appScope.launch { repo.seedIfNeeded() }
    }
}
