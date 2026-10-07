package com.sus7898.lrrviewer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class App : Application(), SingletonImageLoader.Factory {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = graph.imageLoader
}
