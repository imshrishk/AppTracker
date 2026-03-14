package com.apptracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, on-device tracker domain classification.
 * No network calls — all data is embedded at build time.
 *
 * Coverage: ~150 domains across Advertising, Analytics, Data Broker,
 * Social Tracking, Session Recording, Crash Reporting, and Fingerprinting.
 */
@Singleton
class TrackerDomainRepository @Inject constructor() {

    /** Domain suffix → category */
    private val trackerMap: Map<String, String> = mapOf(
        // ── Advertising ──────────────────────────────────────────────────────
        "doubleclick.net"              to "Advertising",
        "googlesyndication.com"        to "Advertising",
        "googleadservices.com"         to "Advertising",
        "googletagmanager.com"         to "Advertising",
        "googletagservices.com"        to "Advertising",
        "adnxs.com"                    to "Advertising",
        "advertising.com"              to "Advertising",
        "moatads.com"                  to "Advertising",
        "adcolony.com"                 to "Advertising",
        "chartboost.com"               to "Advertising",
        "inmobi.com"                   to "Advertising",
        "tapjoy.com"                   to "Advertising",
        "vungle.com"                   to "Advertising",
        "criteo.com"                   to "Advertising",
        "outbrain.com"                 to "Advertising",
        "taboola.com"                  to "Advertising",
        "sharethrough.com"             to "Advertising",
        "pubmatic.com"                 to "Advertising",
        "openx.com"                    to "Advertising",
        "rubiconproject.com"           to "Advertising",
        "sovrn.com"                    to "Advertising",
        "appodeal.com"                 to "Advertising",
        "amazon-adsystem.com"          to "Advertising",
        "media.net"                    to "Advertising",
        "smartadserver.com"            to "Advertising",
        "mopub.com"                    to "Advertising",
        "smaato.net"                   to "Advertising",
        "ironsource.com"               to "Advertising",
        "applovin.com"                 to "Advertising",
        "startapp.com"                 to "Advertising",
        "liftoff.io"                   to "Advertising",
        "aerserv.com"                  to "Advertising",
        "inneractive.mobi"             to "Advertising",
        "unityads.unity3d.com"         to "Advertising",
        "fyber.com"                    to "Advertising",
        "verizonmedia.com"             to "Advertising",
        "oath.com"                     to "Advertising",

        // ── Analytics ────────────────────────────────────────────────────────
        "google-analytics.com"         to "Analytics",
        "app-measurement.com"          to "Analytics",
        "appsflyer.com"                to "Analytics",
        "adjust.com"                   to "Analytics",
        "mixpanel.com"                 to "Analytics",
        "amplitude.com"                to "Analytics",
        "segment.com"                  to "Analytics",
        "segment.io"                   to "Analytics",
        "branch.io"                    to "Analytics",
        "kochava.com"                  to "Analytics",
        "tune.com"                     to "Analytics",
        "singular.net"                 to "Analytics",
        "flurry.com"                   to "Analytics",
        "fabric.io"                    to "Analytics",
        "crashlytics.com"              to "Analytics",
        "countly.com"                  to "Analytics",
        "localytics.com"               to "Analytics",
        "intercom.io"                  to "Analytics",
        "heap.io"                      to "Analytics",
        "heapanalytics.com"            to "Analytics",
        "clevertap.com"                to "Analytics",
        "leanplum.com"                 to "Analytics",
        "braze.com"                    to "Analytics",
        "moengage.com"                 to "Analytics",
        "webengage.com"                to "Analytics",
        "urbanairship.com"             to "Analytics",
        "apsalar.com"                  to "Analytics",
        "swrve.com"                    to "Analytics",
        "taplytics.com"                to "Analytics",
        "optimizely.com"               to "Analytics",
        "firebase.google.com"          to "Analytics",
        "firebaselogging.googleapis.com" to "Analytics",
        "appsee.com"                   to "Analytics",
        "devtodev.com"                 to "Analytics",
        "mytracker.ru"                 to "Analytics",
        "appmetrica.yandex.com"        to "Analytics",
        "moengagemoment.com"           to "Analytics",

        // ── Social Tracking ──────────────────────────────────────────────────
        "connect.facebook.net"         to "Social Tracking",
        "fbcdn.net"                    to "Social Tracking",
        "analytics.twitter.com"        to "Social Tracking",
        "ads-twitter.com"              to "Social Tracking",
        "static.ads-twitter.com"       to "Social Tracking",
        "snap.com"                     to "Social Tracking",
        "tr.snapchat.com"              to "Social Tracking",
        "sc-static.net"                to "Social Tracking",
        "tiktok.com"                   to "Social Tracking",
        "byteoversea.com"              to "Social Tracking",
        "muscdn.com"                   to "Social Tracking",
        "linkedin.com"                 to "Social Tracking",
        "ads.linkedin.com"             to "Social Tracking",
        "pinterest.com"                to "Social Tracking",

        // ── Data Brokers / Profiling ──────────────────────────────────────────
        "bluekai.com"                  to "Data Broker",
        "quantserve.com"               to "Data Broker",
        "scorecardresearch.com"        to "Data Broker",
        "comscore.com"                 to "Data Broker",
        "nielsen.com"                  to "Data Broker",
        "tapad.com"                    to "Data Broker",
        "exelate.com"                  to "Data Broker",
        "krux.com"                     to "Data Broker",
        "lotame.com"                   to "Data Broker",
        "liveramp.com"                 to "Data Broker",
        "acxiom.com"                   to "Data Broker",
        "neustar.biz"                  to "Data Broker",
        "demdex.net"                   to "Data Broker",
        "addthis.com"                  to "Data Broker",
        "adsrvr.org"                   to "Data Broker",
        "mediamath.com"                to "Data Broker",
        "turn.com"                     to "Data Broker",
        "eyeota.com"                   to "Data Broker",
        "theadex.com"                  to "Data Broker",
        "zeotap.com"                   to "Data Broker",
        "intentiq.com"                 to "Data Broker",
        "id5-sync.com"                 to "Data Broker",
        "uidapi.com"                   to "Data Broker",

        // ── Session Recording ────────────────────────────────────────────────
        "hotjar.com"                   to "Session Recording",
        "fullstory.com"                to "Session Recording",
        "mouseflow.com"                to "Session Recording",
        "inspectlet.com"               to "Session Recording",
        "sessioncam.com"               to "Session Recording",
        "logrocket.com"                to "Session Recording",
        "smartlook.com"                to "Session Recording",
        "claritymicrosoft.com"         to "Session Recording",
        "clarity.ms"                   to "Session Recording",
        "uxcam.com"                    to "Session Recording",
        "appsee.io"                    to "Session Recording",

        // ── Crash Reporting ──────────────────────────────────────────────────
        "bugsnag.com"                  to "Crash Reporting",
        "sentry.io"                    to "Crash Reporting",
        "newrelic.com"                 to "Crash Reporting",
        "datadog.com"                  to "Crash Reporting",
        "rollbar.com"                  to "Crash Reporting",
        "raygun.io"                    to "Crash Reporting",
        "appdynamics.com"              to "Crash Reporting",
        "dynatrace.com"                to "Crash Reporting",
        "instabug.com"                 to "Crash Reporting",

        // ── Fingerprinting / Device ID ───────────────────────────────────────
        "ioam.de"                      to "Fingerprinting",
        "ipify.org"                    to "Fingerprinting",
        "device-metrics-us.amazon.com" to "Fingerprinting",
        "trk.pinterest.com"            to "Fingerprinting",
        "fingerprint.com"              to "Fingerprinting",
        "fingerprintjs.com"            to "Fingerprinting",
        "fraudlogix.com"               to "Fingerprinting",
        "threatmetrix.com"             to "Fingerprinting",
        "iovation.com"                 to "Fingerprinting"
    )

    /**
     * Returns (isTracker, category) for the queried domain.
     * Checks the exact domain and all parent domains (e.g. "cdn.doubleclick.net" matches "doubleclick.net").
     */
    fun classify(queriedDomain: String): Pair<Boolean, String> {
        val lower = queriedDomain.lowercase().trimEnd('.')
        var check = lower
        while (check.isNotEmpty()) {
            val cat = trackerMap[check]
            if (cat != null) return true to cat
            val dot = check.indexOf('.')
            if (dot < 0) break
            check = check.substring(dot + 1)
        }
        return false to ""
    }

    /** Total number of tracked domain suffixes in the embedded list. */
    fun trackerCount(): Int = trackerMap.size
}
