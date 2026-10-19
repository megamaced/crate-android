package com.megamaced.crate.testing

import java.lang.reflect.Proxy

/**
 * An implementation of interface [T] whose every method throws. Delegate to it
 * (`object : Repo by unimplemented<Repo>() { … }`) and override only what a
 * test exercises, so an unexpected call fails loudly instead of passing.
 */
inline fun <reified T : Any> unimplemented(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException("${T::class.simpleName}.${method.name} is not stubbed")
    } as T
