package com.uroad.zhgs.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import com.uroad.library.utils.VersionUtils
import com.uroad.library.utils.ZipUtils
import com.uroad.rxhttp.RxHttpManager
import com.uroad.rxhttp.download.DownloadListener
import com.uroad.zhgs.activity.LoginActivity
import com.uroad.zhgs.R
import com.uroad.zhgs.activity.VideoPlayerActivity
import com.uroad.zhgs.common.BaseFragment
import com.uroad.zhgs.common.CurrApplication
import com.uroad.zhgs.dialog.CCTVDetailRvDialog
import com.uroad.zhgs.dialog.EventDetailRvDialog
import com.uroad.zhgs.dialog.TrafficJamDetailRvDialog
import com.uroad.zhgs.enumeration.DiagramEventType
import com.uroad.zhgs.model.*
import com.uroad.zhgs.model.sys.AppConfigMDL
import com.uroad.zhgs.utils.DiagramUtils
import com.uroad.zhgs.utils.GsonUtils
import com.uroad.zhgs.webservice.HttpRequestCallback
import com.uroad.zhgs.webservice.WebApiService
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_diagram.*

/**
 * Created by MFB on 2018/8/11.
 * Copyright  2018年 浙江综合交通大数据开发有限公司.
 * 说明：路况导航（简图模式）
 */
class DiagramFragment : BaseFragment() {
    private enum class PoiType(val code: String) {
        /**
         * 事件1006001
        施工1006002
        管制1006003
        快拍 1004001
        服务区 1003001
        收费站 100200
         */
        ACCIDENT("1006001"),
        CONSTRUCTION("1006002"),
        CONTROL("1006003"),
        SNAPSHOT("1006004"),
        TRAFFIC_JAM("1006005"),
        SERVICE_AREA("1003001"),
        TOLLGATE("1002001")
    }

    override fun setBaseLayoutResID(): Int = R.layout.fragment_diagram

    override fun setUp(view: View, savedInstanceState: Bundle?) {
        initSettings()
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun initSettings() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.databaseEnabled = true
        webView.settings.setGeolocationEnabled(true)// 启用地理定位
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webView.settings.allowUniversalAccessFromFileURLs = true
        }
        webView.settings.allowFileAccess = true
        webView.addJavascriptInterface(JavascriptInterface(), "uroadhtml")
    }

    private fun initWebView() {
        webView.setOnKeyListener(View.OnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack()// 返回前一个页面
                return@OnKeyListener true
            }
            return@OnKeyListener false
        })
        webView.webViewClient = MWebViewClient()
        webView.webChromeClient = MWebChromeClient()
    }

    inner class JavascriptInterface {
        //        @android.webkit.JavascriptInterface
//        fun refreshpage() {  // 2．页面刷新
//
//        }

        @android.webkit.JavascriptInterface
        fun uroadplus_showPOI(poitype: String, poiid: String) {
            //只对事故、管制、施工和快拍进行处理
            if (poitype == PoiType.ACCIDENT.code ||
                    poitype == PoiType.CONSTRUCTION.code ||
                    poitype == PoiType.CONTROL.code) {
                getEventDetailsById(poiid)
            } else if (poitype == PoiType.TRAFFIC_JAM.code) {
                getJamEventDetailsById(poiid)
            } else if (poitype == PoiType.SNAPSHOT.code) {
                getCCTVDetailsById(poiid)
            }
        }
    }

    private fun getEventDetailsById(eventids: String) {
        doRequest(WebApiService.EVENT_DETAIL, WebApiService.eventDetailsByIdParams(eventids, getUserId()), object : HttpRequestCallback<String>() {
            override fun onPreExecute() {
                showLoading()
            }

            override fun onSuccess(data: String?) {
                endLoading()
                if (GsonUtils.isResultOk(data)) {
                    val dataMDLs = GsonUtils.fromDataToList(data, EventMDL::class.java)
                    onEventDetail(dataMDLs)
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                endLoading()
                onHttpError(e)
            }
        })
    }

    private fun onEventDetail(mdLs: MutableList<EventMDL>) {
        if (mdLs.size == 0) {
            showShortToast("暂无数据哟~")
            return
        }
        val dialog = EventDetailRvDialog(context, mdLs)
        dialog.setOnViewClickListener(object : EventDetailRvDialog.OnViewClickListener {
            override fun onViewClick(dataMDL: EventMDL, position: Int, type: Int) {
                if (!isLogin()) openActivity(LoginActivity::class.java)
                else when (type) {
                    1 -> saveIsUseful(dataMDL, 1, 1, position, dialog)
                    2 -> saveIsUseful(dataMDL, 1, 2, position, dialog)
                    3 -> saveSubscribe(dataMDL, 1, position, dialog)
                }
            }
        })
        dialog.show()
    }

    /*是否有用*/
    private fun saveIsUseful(item: MutilItem, itemType: Int, type: Int, position: Int, dialog: Dialog) {
        val eventId: String? = if (itemType == 1) {
            (item as EventMDL).eventid
        } else {
            (item as TrafficJamMDL).eventid
        }
        doRequest(WebApiService.SAVE_IS_USEFUL, WebApiService.isUsefulParams(eventId, getUserId(), type), object : HttpRequestCallback<String>() {
            override fun onPreExecute() {
                showLoading()
            }

            override fun onSuccess(data: String?) {
                endLoading()
                if (GsonUtils.isResultOk(data)) {
                    if (itemType == 1) {
                        val mdl = item as EventMDL
                        mdl.isuseful = type
                        (dialog as EventDetailRvDialog).notifyItemChanged(position, mdl)
                    } else {
                        val mdl = item as TrafficJamMDL
                        mdl.isuseful = type
                        (dialog as TrafficJamDetailRvDialog).notifyItemChanged(position, mdl)
                    }
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                endLoading()
                onHttpError(e)
            }
        })
    }

    /*保存订阅*/
    private fun saveSubscribe(item: MutilItem, itemType: Int, position: Int, dialog: Dialog) {
        val eventId: String?
        val subType: String?
        if (itemType == 1) {
            val mdl = item as EventMDL
            eventId = mdl.eventid
            subType = mdl.getSubType()
        } else {
            val mdl = item as TrafficJamMDL
            eventId = mdl.eventid
            subType = mdl.getSubType()
        }
        doRequest(WebApiService.SAVE_SUBSCRIBE, WebApiService.saveSubscribeParams(getUserId(), subType, eventId),
                object : HttpRequestCallback<String>() {
                    override fun onPreExecute() {
                        showLoading("保存订阅…")
                    }

                    override fun onSuccess(data: String?) {
                        endLoading()
                        if (GsonUtils.isResultOk(data)) {
                            showShortToast("订阅成功")
                            if (itemType == 1) {
                                val mdl = item as EventMDL
                                mdl.subscribestatus = 1
                                (dialog as EventDetailRvDialog).notifyItemChanged(position, mdl)
                            } else {
                                val mdl = item as TrafficJamMDL
                                mdl.subscribestatus = 1
                                (dialog as TrafficJamDetailRvDialog).notifyItemChanged(position, mdl)
                            }
                        } else showShortToast(GsonUtils.getMsg(data))
                    }

                    override fun onFailure(e: Throwable, errorMsg: String?) {
                        endLoading()
                        onHttpError(e)
                    }
                })
    }

    /*获取拥堵详情*/
    private fun getJamEventDetailsById(jamids: String?) {
        doRequest(WebApiService.TRAFFIC_JAM_DETAIL, WebApiService.trafficJamDetailsByIdParams(jamids, getUserId()), object : HttpRequestCallback<String>() {
            override fun onPreExecute() {
                showLoading()
            }

            override fun onSuccess(data: String?) {
                endLoading()
                if (GsonUtils.isResultOk(data)) {
                    val mdLs = GsonUtils.fromDataToList(data, TrafficJamMDL::class.java)
                    if (mdLs.size == 0) {
                        showShortToast("暂无数据哟~")
                    } else {
                        onTrafficJamDetail(mdLs)
                    }
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                endLoading()
                onHttpError(e)
            }
        })
    }

    /*拥堵详情*/
    private fun onTrafficJamDetail(mdLs: MutableList<TrafficJamMDL>) {
        TrafficJamDetailRvDialog(context, mdLs).setOnViewClickListener(object : TrafficJamDetailRvDialog.OnViewClickListener {
            override fun onViewClick(mdl: TrafficJamMDL, position: Int, type: Int, dialog: TrafficJamDetailRvDialog) {
                if (!isLogin()) openActivity(LoginActivity::class.java)
                else when (type) {
                    1 -> saveIsUseful(mdl, 2, 1, position, dialog)
                    2 -> saveIsUseful(mdl, 2, 2, position, dialog)
                    3 -> saveSubscribe(mdl, 2, position, dialog)
                }
            }
        }).show()
    }

    /*获取快拍数据*/
    private fun getCCTVDetailsById(cctvIds: String) {
        doRequest(WebApiService.CCTV_DETAIL, WebApiService.cctvDetailParams(cctvIds), object : HttpRequestCallback<String>() {
            override fun onPreExecute() {
                showLoading()
            }

            override fun onSuccess(data: String?) {
                endLoading()
                if (GsonUtils.isResultOk(data)) {
                    val dataMDLs = GsonUtils.fromDataToList(data, SnapShotMDL::class.java)
                    onSnapShotDetail(dataMDLs)
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                endLoading()
                onHttpError(e)
            }
        })
    }

    //监控快拍类型
    private fun onSnapShotDetail(mdLs: MutableList<SnapShotMDL>) {
        val dialog = CCTVDetailRvDialog(context, mdLs)
        dialog.setOnPhotoClickListener(object : CCTVDetailRvDialog.OnPhotoClickListener {
            override fun onPhotoClick(position: Int, mdl: SnapShotMDL) {
                getRoadVideo(mdl.resid, mdl.shortname)
            }
        })
        dialog.show()
    }

    /*获取快拍请求流地址*/
    private fun getRoadVideo(resId: String?, shortName: String?) {
        doRequest(WebApiService.ROAD_VIDEO, WebApiService.roadVideoParams(resId), object : HttpRequestCallback<String>() {
            override fun onPreExecute() {
                showLoading()
            }

            override fun onSuccess(data: String?) {
                endLoading()
                if (GsonUtils.isResultOk(data)) {
                    val mdl = GsonUtils.fromDataBean(data, RtmpMDL::class.java)
                    mdl?.rtmpIp?.let {
                        openActivity(VideoPlayerActivity::class.java, Bundle().apply {
                            putBoolean("isLive", true)
                            putString("url", it)
                            putString("title", shortName)
                        })
                    }
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                endLoading()
                onHttpError(e)
            }
        })
    }

    private inner class MWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest?): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.loadUrl(request?.url.toString())
            } else {
                webView.loadUrl(request?.toString())
            }
            return true
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.proceed()
        }

    }

    inner class MWebChromeClient : WebChromeClient() {

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, true, false)
            super.onGeolocationPermissionsShowPrompt(origin, callback)
        }
    }

    override fun initData() {
        if (DiagramUtils.diagramExists()) {
            loadUrl()
        } else {
            doRequest(WebApiService.APP_CONFIG, WebApiService.getBaseParams(), object : HttpRequestCallback<String>() {
                override fun onSuccess(data: String?) {
                    val mdLs = GsonUtils.fromDataToList(data, AppConfigMDL::class.java)
                    for (item in mdLs) {
                        if (TextUtils.equals(item.confid, AppConfigMDL.Type.DIAGRAM_VER.CODE)) {
                            updateData(item)
                            break
                        }
                    }
                }

                override fun onFailure(e: Throwable, errorMsg: String?) {
                    onHttpError(e)
                }
            })
        }
    }

    private fun updateData(mdl: AppConfigMDL) {
        val verLocal = DiagramUtils.getVersionLocal(context)
        if (VersionUtils.isNeedUpdate(mdl.conf_ver, verLocal)) {  //判断服务器版本是否大于本地保存的版本号
            DiagramUtils.deleteAllFile()   //先删除文件夹下所有文件
            doDownload(mdl.url)
        } else {
            if (DiagramUtils.diagramExists()) {
                loadUrl()
            } else {
                doDownload(mdl.url)
            }
        }
        DiagramUtils.saveVersionSer(context, mdl.conf_ver)  //保存服务器版本号到本地
    }

    //下载简图
    private fun doDownload(url: String?) {
        if (TextUtils.isEmpty(url)) {
            showLongToast("下载链接不存在")
            return
        }
        addDisposable(RxHttpManager.downloadFile(url, CurrApplication.DIAGRAM_PATH, null, object : DownloadListener() {
            override fun onStart(disposable: Disposable?) {
                showLoading("正在下载简图…")
            }

            override fun onFinish(filePath: String) {
                ZipUtils.UnZipFolder(filePath, CurrApplication.DIAGRAM_PATH)
                if (DiagramUtils.diagramExists()) loadUrl()
            }

            override fun onError(e: Throwable, errorMsg: String?) {
                onHttpError(e)
            }

            override fun onComplete() {
                endLoading()
            }
        }))
    }

    private fun loadUrl() {
        webView.loadUrl(DiagramUtils.diagramUrl())
        loadEvent(DiagramEventType.Accident.code, 1)
        loadEvent(DiagramEventType.Construction.code, 0)  //默认关闭施工
        loadEvent(DiagramEventType.Control.code, 1)
        loadEvent(DiagramEventType.TollGate.code, 1)
        loadEvent(DiagramEventType.TrafficJam.code, 0) //默认关闭拥堵
        loadEvent(DiagramEventType.PileNumber.code, 0)  //桩号默认关闭
        loadEvent(DiagramEventType.ServiceArea.code, 1)
        loadEvent(DiagramEventType.Snapshot.code, 1)
    }

    fun onEvent(type: Int, isChecked: Boolean) {
        val isDisplay = if (isChecked) 1 else 0
        when (type) {
            1 -> loadEvent(DiagramEventType.Accident.code, isDisplay) //事故是否选中
            2 -> loadEvent(DiagramEventType.Control.code, isDisplay) //管制
            3 -> loadEvent(DiagramEventType.Construction.code, isDisplay) //施工
            4 -> loadEvent(DiagramEventType.TrafficJam.code, isDisplay) //拥堵
            5 -> loadEvent(DiagramEventType.PileNumber.code, isDisplay)//桩号
            6 -> loadEvent(DiagramEventType.TollGate.code, isDisplay) //收费站
            7 -> loadEvent(DiagramEventType.ServiceArea.code, isDisplay) //服务区
            8 -> loadEvent(DiagramEventType.Snapshot.code, isDisplay) //快拍
        }
    }

    //isdisplay	是否展示	1显示0隐藏
    private fun loadEvent(poiType: String, isDisplay: Int) {
        loadJs("uroadplus_web_showPOILayer", "'$poiType','$isDisplay'")
    }

    //事件1006001 施工1006002 管制1006005 快拍 1004001 服务区 1003001 收费站 1002001 桩号 1001001
    private fun loadJs(funName: String, data: String) {
        val js = "javascript:$funName($data)"
        webView.loadUrl(js)
    }

    //SVG简图交互-刷新简图
//    private fun refreshSVG() {
//        val js = "javascript:uroadplus_web_refreshSVG()"
//        webView.loadUrl(js)
//    }

    //SVG简图交互-放大缩小简图
    fun enlargeSVG() {
        webView.loadUrl("javascript:enlargeSVG()")
    }

    fun narrowSVG() {
        webView.loadUrl("javascript:narrowSVG()")
    }
}