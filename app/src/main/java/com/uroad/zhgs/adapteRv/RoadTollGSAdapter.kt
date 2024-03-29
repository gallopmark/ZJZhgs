package com.uroad.zhgs.adapteRv

import android.content.Context
import android.support.v4.content.ContextCompat
import android.view.View
import com.uroad.zhgs.R
import com.uroad.zhgs.model.RoadTollGSMDL
import com.uroad.zhgs.rv.BaseArrayRecyclerAdapter

/**
 * @author MFB
 * @create 2018/9/19
 * @describe 路径路费 高速列表适配器
 */
class RoadTollGSAdapter(context: Context, mDatas: MutableList<RoadTollGSMDL>)
    : BaseArrayRecyclerAdapter<RoadTollGSMDL>(context, mDatas) {
    private val colorBlack = ContextCompat.getColor(context, R.color.appTextColor)
    private val colorGray = ContextCompat.getColor(context, R.color.blow_gray)
    private val colorFC = ContextCompat.getColor(context, R.color.color_fc)
    private val colorF7 = ContextCompat.getColor(context, R.color.color_f7)

    private var selectPos: Int = 0

    override fun bindView(viewType: Int): Int = R.layout.item_roadtoll_gs

    override fun onBindHoder(holder: RecyclerHolder, t: RoadTollGSMDL, position: Int) {
        if (position == selectPos) {
            holder.setVisibility(R.id.ivSelect, View.VISIBLE)
            holder.setTextColor(R.id.tv, colorBlack)
            holder.itemView.setBackgroundColor(colorFC)
        } else {
            holder.setVisibility(R.id.ivSelect, View.INVISIBLE)
            holder.setTextColor(R.id.tv, colorGray)
            holder.itemView.setBackgroundColor(colorF7)
        }
        holder.setText(R.id.tv, t.shortname)
    }

    fun setSelectPos(position: Int) {
        this.selectPos = position
        notifyDataSetChanged()
    }
}