package com.mobileagent

import android.app.Application

class AgentApp : Application() {
    lateinit var agentGraph: AgentGraph
        private set

    override fun onCreate() {
        super.onCreate()
        agentGraph = AgentGraph.create(this)
    }
}
