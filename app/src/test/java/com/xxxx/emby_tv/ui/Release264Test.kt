package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.connect.ConnectForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release264Test {
    @Test
    fun newInstallDefaultsToHttps443AndSavedUrlsStay() {
        assertEquals(Triple("https", "", "443"), ConnectForm.parseServerUrl(null))
        assertEquals(Triple("https", "", "443"), ConnectForm.parseServerUrl(""))
        assertEquals(Triple("http", "192.168.1.8", "8096"), ConnectForm.parseServerUrl("http://192.168.1.8:8096"))
        assertEquals(Triple("https", "emby.example", "8920"), ConnectForm.parseServerUrl("https://emby.example:8920"))
        assertEquals(Triple("https", "v.emby26.cc", "443"), ConnectForm.parseServerUrl("https://v.emby26.cc"))
        assertEquals(Triple("http", "nas.local", "8096"), ConnectForm.parseServerUrl("http://nas.local"))
        assertEquals("8920", ConnectForm.portFor("https", "8920"))
        assertEquals("8096", ConnectForm.portFor("http", "443"))
        assertEquals("443", ConnectForm.portFor("https", "8096"))
        assertEquals("主机地址", ConnectForm.HOST_PLACEHOLDER)
        assertTrue(ConnectForm.loginButtonShown())
    }

    @Test
    fun phoneLoginSubmitsAndSearchProxyStaySendToTv() {
        val form = ConnectForm.loginFormFragment()
        assertTrue(form.contains("value=\"https\""))
        assertTrue(form.contains("value=\"443\""))
        assertTrue(form.contains(ConnectForm.HOST_PLACEHOLDER))
        assertTrue(form.contains(">${ConnectForm.PHONE_LOGIN_SUBMIT}<"))
        assertFalse(form.contains(ConnectForm.PHONE_PROXY_SUBMIT))
        assertEquals("登录", ConnectForm.PHONE_LOGIN_SUBMIT)
        assertEquals("发送到电视", ConnectForm.PHONE_PROXY_SUBMIT)
        assertEquals("在电视上搜索", ConnectForm.PHONE_SEARCH_SUBMIT)
        assertTrue(ConnectForm.shouldAutoLogin("v.emby26.cc", "user"))
        assertFalse(ConnectForm.shouldAutoLogin("", "user"))
        assertFalse(ConnectForm.shouldAutoLogin("host", " "))
        assertTrue(ConnectForm.shouldAutoLogin("host", "user"))
    }
}
