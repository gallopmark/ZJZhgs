package com.uroad.zhgs.model

import com.amap.api.maps.model.LatLng
import com.uroad.zhgs.R
import com.uroad.zhgs.cluster.ClusterItem
import java.io.Serializable

/**
 *Created by MFB on 2018/8/23.
 * 收费站
 * picurl": "http:\/\/zhgs.u-road.com\/ZJAppApi\/newcode\/s2.png",
"poiid": "1027",
"longitude": "120.234001",
"latitude": "30.289700",
"shortname": "S2杭甬高速",
"name": "杭州（彭埠）站",
"distance": "0.84",
"upstatus": "2",
"downstatus": "2",
"poistatus": "1", 	0 关闭 ； 1 开启
"detailurl": "http:\/\/zhgs.u-road.com\/ZJAppView\/stationDetail.html?dataid=1027"
 */
class TollGateMDL : MutilItem, ClusterItem, Serializable {
    override fun getItemType(): Int = 9

    var picurl: String? = null
    var poiid: String? = null
    var longitude: Double? = 0.0
    var latitude: Double? = 0.0
    var shortname: String? = null
    var name: String? = null
    var distance: String? = null
    var upstatus: Int? = 0
    var downstatus: Int? = 0
    var poistatus: Int? = 0
    var detailurl: String? = null

    var markerIcon: Int = R.mipmap.ic_marker_toll_icon
    var markerBigIco: Int = R.mipmap.ic_marker_toll_big_icon

    fun latitude(): Double {
        latitude?.let { return it }
        return 0.0
    }

    fun longitude(): Double {
        longitude?.let { return it }
        return 0.0
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            !is TollGateMDL -> false
            else -> this === other || detailurl == other.detailurl
        }
    }

    override fun hashCode(): Int {
        return 31 + (detailurl?.hashCode() ?: 0)
    }

    override fun getPosition(): LatLng = LatLng(latitude(), longitude())
    override fun getMarkerSmallIcon(): Int = markerIcon
    override fun getMarkerBigIcon(): Int = markerBigIco
}