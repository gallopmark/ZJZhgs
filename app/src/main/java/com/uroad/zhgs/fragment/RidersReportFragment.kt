package com.uroad.zhgs.fragment

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.util.ArrayMap
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SimpleItemAnimator
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.scwang.smartrefresh.layout.listener.OnRefreshLoadMoreListener
import com.uroad.library.utils.NetworkUtils
import com.uroad.zhgs.R
import com.uroad.zhgs.adapteRv.RidersReportAdapter
import com.uroad.zhgs.model.RidersReportMDL
import com.uroad.zhgs.utils.GsonUtils
import com.uroad.zhgs.utils.InputMethodUtils
import com.uroad.zhgs.webservice.HttpRequestCallback
import com.uroad.zhgs.webservice.WebApiService
import kotlinx.android.synthetic.main.fragment_riders_report.*
import android.widget.ImageView
import com.airbnb.lottie.LottieAnimationView
import com.amap.api.location.AMapLocation
import com.uroad.library.rxbus.RxBus
import com.uroad.library.utils.DisplayUtils
import com.uroad.zhgs.activity.CameraActivity
import com.uroad.zhgs.activity.RidersReportActivity
import com.uroad.zhgs.activity.VideoActivity
import com.uroad.zhgs.common.CameraFragment
import com.uroad.zhgs.helper.AppLocalHelper
import com.uroad.zhgs.rxbus.MessageEvent
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.layout_empty.*
import kotlinx.android.synthetic.main.layout_error.*
import tv.danmaku.ijk.media.player.IjkMediaPlayer


/**
 * @author MFB
 * @create 2018/10/9
 * @describe 车友爆料
 */
class RidersReportFragment : CameraFragment() {
    private val mDatas = ArrayList<RidersReportMDL>()
    private lateinit var adapter: RidersReportAdapter
    private var longitude: Double = 0.toDouble()
    private var latitude: Double = 0.toDouble()
    private var index = 1
    private val size = 10
    private val hashMap = ArrayMap<Int, String>()
    private var isAnim = false
    private var isMy = false
    private var myFollow = false //获取我的关注
    private var type: String? = null
    // 子按钮列表
    private val buttonItems = ArrayList<ImageView>()
    // 标识当前按钮弹出与否，1代表已经未弹出，-1代表已弹出
    private var isOpen = false
    private var mediaPlayer: IjkMediaPlayer? = null
    private var playIndex = -1

    override fun setBaseLayoutResID(): Int = R.layout.fragment_riders_report

    override fun setUp(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            isAnim = it.getBoolean("anim", false)
            isMy = it.getBoolean("isMy", false)
            myFollow = it.getBoolean("myFollow", false)
        }
        if (isMy) {
            type = "my"
            ivCreate.visibility = View.GONE
        } else if (myFollow) {
            type = "myfollow"
        }
        initRv()
        refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onRefresh(refreshLayout: RefreshLayout?) {
                index = 1
                loadData()
            }

            override fun onLoadMore(refreshLayout: RefreshLayout?) {
                loadData()
            }
        })
        initMenuButton()
        addDisposable(RxBus.getDefault().toObservable(MessageEvent::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    if (event.obj != null && event.obj is String) {
                        val followUserId = event.obj as String
                        for (item in mDatas) {
                            if (TextUtils.equals(followUserId, item.userid)) item.isfollow = 1
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        index = 1
                        loadData()
                    }
                })
        requestLocationPermissions(object : RequestLocationPermissionCallback {
            override fun doAfterGrand() {
                openLocation()
            }

            override fun doAfterDenied() {
                refreshLayout.autoRefresh()
            }
        })
    }

    override fun afterLocation(location: AMapLocation) {
        this.longitude = location.longitude
        this.latitude = location.latitude
        refreshLayout.autoRefresh()
        closeLocation()
    }

    override fun locationFailure() {
        refreshLayout.autoRefresh()
    }

    private fun initRv() {
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        recyclerView.layoutManager = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        adapter = RidersReportAdapter(context, mDatas).apply {
            setOnAdapterChildClickListener(object : RidersReportAdapter.OnAdapterChildClickListener {
                override fun onImageClick(position: Int, photos: MutableList<String>) {
                    val pics = ArrayList<String>()
                    for (i in 0 until photos.size) {
                        if (!TextUtils.isEmpty(photos[i])) {
                            pics.add(photos[i])
                        }
                    }
                    showBigPic(position, pics)
                }

                override fun onFollowClick(position: Int) { //点击关注
                    follow(position)
                }

                override fun onVoiceClick(position: Int, lottieView: LottieAnimationView) {
                    if (position in 0 until mDatas.size) {
                        stopPlay()
                        playVoice(mDatas[position].imgurls, position)
                    }
                }

                override fun onVideoClick(position: Int) {
                    if (position in 0 until mDatas.size) {
                        openActivity(VideoActivity::class.java, Bundle().apply { putString("url", mDatas[position].imgurls) })
                    }
                }

                override fun onCommentClick(position: Int, view: View) {  //点击评论
                    showBottomView(view, position, 0.toString(), "", "")
                }

                override fun onChildCommentClick(view: View, position: Int, parentid: String?, userid: String?, username: String?) {
                    showBottomView(view, position, parentid, userid, username)
                }

                override fun onSupportClick(position: Int, view: View) {  //点击点赞
                    if (TextUtils.isEmpty(mDatas[position].issupport)) {   //没有点赞过
                        support(position, 1)
                    } else {  //已经点赞,取消点赞
                        support(position, 2)
                    }
                }
            })
            recyclerView.setOnTouchListener { _, _ ->
                etComment.clearFocus()
                InputMethodUtils.hideSoftInput(context, etComment)
                llComment.visibility = View.GONE
                return@setOnTouchListener false
            }
        }
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false
    }

    private fun initMenuButton() {
        buttonItems.add(ivDefault)
        buttonItems.add(ivVideo)
        buttonItems.add(ivVoice)
        ivCreate.setOnClickListener {
            if (!isOpen) {
                openMenu()
            } else {
                closeMenu()
            }
        }
        rlContainer.setOnClickListener { if (isOpen) closeMenu() }
        val listener = View.OnClickListener {
            if (AppLocalHelper.isAuthBLFB(context) && !isAuth()) {
                showTipsDialog(context.getString(R.string.dialog_default_title), context.getString(R.string.without_auth))
            } else {
                when (it.id) {
                    R.id.ivVoice -> openActivity(Intent(context, RidersReportActivity::class.java).apply { type = RidersReportActivity.TYPE_VOICE })
                    R.id.ivVideo -> onVideoRecord()
                    else -> openActivity(Intent(context, RidersReportActivity::class.java).apply { type = RidersReportActivity.TYPE_DEFAULT })
                }
            }
            closeMenu()
        }
        ivDefault.setOnClickListener(listener)
        ivVoice.setOnClickListener(listener)
        ivVideo.setOnClickListener(listener)
    }

    fun isMenuOpen() = isOpen
    private fun openMenu() {
        buttonAction()
        buttonAnimation()
        isOpen = true
    }

    fun closeMenu() {
        buttonAction()
        buttonAnimation()
        isOpen = false
    }

    private fun buttonAction() {
        ivCreate.isEnabled = false
        ivDefault.isEnabled = false
        ivVoice.isEnabled = false
        ivVideo.isEnabled = false
        ivCreate.postDelayed({
            ivCreate.isEnabled = true
            ivDefault.isEnabled = true
            ivVoice.isEnabled = true
            ivVideo.isEnabled = true
        }, 350)
    }

    /**
     * 按钮移动动画
     * @params 子按钮列表
     * @params 弹出时圆形半径radius
     */
    private fun buttonAnimation() {
        val radius = DisplayUtils.dip2px(context, 100f)
        ivCreate.setImageResource(R.mipmap.ic_riders_report_close)
        rlContainer.visibility = View.VISIBLE
        for (i in 0 until buttonItems.size) {
            // 将按钮设为可见
            buttonItems[i].visibility = View.VISIBLE
            // 按钮在X、Y方向的移动距离
            val flag = if (isOpen) -1 else 1
            val distanceX = (flag * radius * (Math.cos(getAngle(buttonItems.size, i)))).toFloat()
            val distanceY = -(flag * radius * (Math.sin(getAngle(buttonItems.size, i)))).toFloat()
            // X方向移动
            ObjectAnimator.ofFloat(buttonItems[i], "x", buttonItems[i].x, buttonItems[i].x + distanceX).apply {
                duration = 200
                startDelay = 100
                start()
                if (isOpen) addListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        for (button in buttonItems) button.visibility = View.INVISIBLE
                        ivCreate.setImageResource(R.mipmap.ic_riders_report_open)
                        rlContainer.visibility = View.GONE
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        rlContainer.visibility = View.GONE
                    }

                    override fun onAnimationStart(animation: Animator) {}
                })
            }
            // Y方向移动
            ObjectAnimator.ofFloat(buttonItems[i], "y", buttonItems[i].y, buttonItems[i].y + distanceY).apply {
                duration = 200
                startDelay = 100
                start()
            }
            // 按钮旋转
            ObjectAnimator.ofFloat(buttonItems[i], "rotation", 0f, 360f).apply {
                duration = 200
                startDelay = 100
                start()
            }
        }
    }

    private fun getAngle(total: Int, index: Int): Double = Math.toRadians((90 / (total - 1) * index + 90).toDouble())

    private fun loadData() {
        doRequest(WebApiService.USER_EVELT_LIST, WebApiService.userEventListParams(getUserId(), longitude, latitude, type, index, size),
                object : HttpRequestCallback<String>() {
                    override fun onPreExecute() {
                        onLoadingStart()
                    }

                    override fun onSuccess(data: String?) {
                        finishLoad()
                        if (GsonUtils.isResultOk(data)) {
                            val list = GsonUtils.fromDataToList(data, RidersReportMDL::class.java)
                            updateData(list)
                        } else {
                            showShortToast(GsonUtils.getMsg(data))
                        }
                    }

                    override fun onFailure(e: Throwable, errorMsg: String?) {
                        finishLoad()
                        if (index == 1) {
                            onPageError()
                        } else
                            onHttpError(e)
                    }
                })
    }

    private fun onLoadingStart() {
        refreshLayout.visibility = View.VISIBLE
        mEmptyTv.visibility = View.GONE
        llErrorLayout.visibility = View.GONE
    }

    private fun finishLoad() {
        if (refreshLayout.isRefreshing) refreshLayout.finishRefresh()
        if (refreshLayout.isLoading) refreshLayout.finishLoadMore()
    }

    private fun updateData(list: MutableList<RidersReportMDL>) {
        if (index == 1) mDatas.clear()
        mDatas.addAll(list)
        adapter.notifyDataSetChanged()
        if (list.size < size) {
            refreshLayout.setNoMoreData(true)
        } else {
            refreshLayout.setNoMoreData(false)
        }
        if (index == 1 && mDatas.size == 0) {
            onPageNoData()
        } else index += 1
    }

    private fun onPageNoData() {
        refreshLayout.visibility = View.GONE
        mEmptyTv.visibility = View.VISIBLE
        mEmptyTv.text = context.getString(R.string.empty_riders_report)
    }

    private fun onPageError() {
        refreshLayout.visibility = View.GONE
        llErrorLayout.visibility = View.VISIBLE
        val drawableTop: Drawable?
        if (!NetworkUtils.isConnected(context)) {
            mErrorTv.text = resources.getString(R.string.nonetwork)
            drawableTop = ContextCompat.getDrawable(context, R.mipmap.ic_nonetwork)
        } else {
            mErrorTv.text = resources.getString(R.string.connect_error)
            drawableTop = ContextCompat.getDrawable(context, R.mipmap.ic_connect_error)
        }
        mErrorTv.setCompoundDrawablesWithIntrinsicBounds(null, drawableTop, null, null)
        tvReload.setOnClickListener {
            llErrorLayout.visibility = View.GONE
            refreshLayout.visibility = View.VISIBLE
            loadData()
        }
    }

    //关注
    private fun follow(position: Int) {
        if (position !in 0 until mDatas.size) return
        val status = if (mDatas[position].isfollow == 1) {   //未关注，则创建关注
            1
        } else {   //已关注，则取消关注
            0
        }
        val followUserId = mDatas[position].userid
        doRequest(WebApiService.UPDATE_FOLLOW_STATUS, WebApiService.updateFollowParams(getUserId(), followUserId, status), object : HttpRequestCallback<String>() {
            override fun onPreExecute() {
                showLoading()
            }

            override fun onSuccess(data: String?) {
                endLoading()
                if (GsonUtils.isResultOk(data)) {
                    if (status == 1) {
                        showShortToast("关注成功")
                        for (item in mDatas) {
                            if (TextUtils.equals(followUserId, item.userid)) item.isfollow = 0
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        showShortToast("已取消关注")
                        for (item in mDatas) {
                            if (TextUtils.equals(followUserId, item.userid)) item.isfollow = 1
                        }
                        adapter.notifyDataSetChanged()
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

    //点赞或取消点赞  type=1点赞 type=2取消点赞
    private fun support(position: Int, type: Int) {
        if (position !in 0 until mDatas.size) return
        val eventuserid = mDatas[position].eventid ?: ""
        val status = if (type == 1) 1 else 0
        doRequest(WebApiService.SUPPORT, WebApiService.supportParams(eventuserid, getUserId(), status.toString()), object : HttpRequestCallback<String>() {
            override fun onSuccess(data: String?) {
                if (GsonUtils.isResultOk(data)) {
                    var supportCount = mDatas[position].supportcount
                    if (type == 1) {   //点赞成功
                        mDatas[position].issupport = "support"
                        supportCount += 1
                        mDatas[position].supportcount = supportCount
                    } else {   //取消点赞成功
                        mDatas[position].issupport = null
                        supportCount -= 1
                        mDatas[position].supportcount = supportCount
                    }
                    adapter.notifyDataSetChanged()
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                onHttpError(e)
            }
        })
    }

    private fun showBottomView(itemBottomView: View, position: Int,
                               parentid: String?, touserid: String?, tousername: String?) {
        //注意，由于弹出输入法需要一定的时间，所以该方法要延迟500ms计算，500ms为经验值，仅供参考。
        InputMethodUtils.showSoftInput(context, etComment)
        llComment.visibility = View.VISIBLE
        etComment.requestFocus()
        etComment.isFocusable = true
        etComment.setText(hashMap[position])
        etComment.setSelection(etComment.text.length)
        mButton.isEnabled = !TextUtils.isEmpty(etComment.text)
        if (mButton.isEnabled) {
            mButton.setBackgroundResource(R.drawable.bg_button_comment_selector)
        } else {
            mButton.setBackgroundResource(R.drawable.bg_button_comment_default_corners_5dp)
        }
        if (position == mDatas.size - 1) {   //最后一个
            recyclerView.postDelayed({ recyclerView.scrollToPosition(mDatas.size - 1) }, 500)
        } else {
            llComment.postDelayed({
                val position1 = IntArray(2)
                itemBottomView.getLocationOnScreen(position1)
                val position2 = IntArray(2)
                llComment.getLocationOnScreen(position2)
                recyclerView.scrollBy(0, position1[1] - position2[1])
            }, 500)
        }
        etComment.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {
                hashMap[position] = s.toString()
                mButton.isEnabled = !TextUtils.isEmpty(s.toString().trim())
                if (mButton.isEnabled) {
                    mButton.setBackgroundResource(R.drawable.bg_button_comment_selector)
                } else {
                    mButton.setBackgroundResource(R.drawable.bg_button_comment_default_corners_5dp)
                }
            }
        })
        mButton.setOnClickListener {
            etComment.clearFocus()
            InputMethodUtils.hideSoftInput(context, etComment)
            llComment.visibility = View.GONE
            eventComment(position, etComment.text.toString(), parentid, touserid, tousername)
        }
    }

    //用户报料-评论
    private fun eventComment(position: Int, content: String, parentid: String?,
                             touserid: String?, tousername: String?) {
        if (position !in 0..mDatas.size) return
        val eventid = mDatas[position].eventid ?: ""
        val username = getUserName()
        doRequest(WebApiService.EVENT_COMMENT, WebApiService.eventCommentParams(eventid, getUserId(), username,
                content, parentid, touserid, tousername), object : HttpRequestCallback<String>() {
            override fun onSuccess(data: String?) {
                if (GsonUtils.isResultOk(data)) {   //评论成功
                    val comment = RidersReportMDL.Comment().apply {
                        this.eventid = eventid
                        this.userid = getUserId()
                        this.usercomment = content
                        this.username = username
                        this.touserid = touserid
                        this.tousername = tousername
                    }
                    val commentCount = mDatas[position].commentcount
                    mDatas[position].commentcount = commentCount + 1
                    mDatas[position].getCommentList().add(comment)
                    adapter.notifyDataSetChanged()
                    etComment.setText("")
                } else {
                    showShortToast(GsonUtils.getMsg(data))
                }
            }

            override fun onFailure(e: Throwable, errorMsg: String?) {
                onHttpError(e)
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123 && resultCode == Activity.RESULT_OK && data != null) {
            val type = data.getStringExtra("TYPE")
            val url = data.getStringExtra("url")
            if (TextUtils.equals(type, "PHOTO")) {
                openActivity(Intent(context, RidersReportActivity::class.java).apply {
                    this.type = RidersReportActivity.TYPE_DEFAULT
                    putExtras(Bundle().apply { putString("url", url) })
                })
            } else {
                val firstFrame = data.getStringExtra("firstFrame")
                openActivity(Intent(context, RidersReportActivity::class.java).apply {
                    this.type = RidersReportActivity.TYPE_VIDEO
                    putExtras(Bundle().apply {
                        putString("url", url)
                        putString("firstFrame", firstFrame)
                    })
                })
            }
        }
    }

    private fun playVoice(url: String?, position: Int) {
        playIndex = position
        mediaPlayer = IjkMediaPlayer().apply {
            try {
                reset()
                dataSource = url
                prepareAsync()
                setOnPreparedListener {
                    it.start()
                    mDatas[position].isVoicePlaying = true
                    adapter.notifyItemChanged(position)
                }
                setOnCompletionListener {
                    mDatas[position].isVoicePlaying = false
                    adapter.notifyItemChanged(position)
                }
                setOnErrorListener { _, _, _ ->
                    mDatas[position].isVoicePlaying = false
                    adapter.notifyItemChanged(position)
                    return@setOnErrorListener false
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun stopPlay() {
        if (playIndex in 0 until mDatas.size) {
            mDatas[playIndex].isVoicePlaying = false
            adapter.notifyItemChanged(playIndex)
        }
        mediaPlayer?.let {
            it.stop()
            it.release()
            mediaPlayer = null
        }
    }

    private fun onVideoRecord() {
        if (hasCamera()) startCamera()
        else {
            requestCamera(object : OnRequestCameraCallback {
                override fun onGranted() {
                    startCamera()
                }
            })
        }
    }

    private fun startCamera() {
        openActivityForResult(CameraActivity::class.java, 123)
    }

    override fun onPause() {
        stopPlay()
        super.onPause()
    }
}