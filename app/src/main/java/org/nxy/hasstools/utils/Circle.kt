package org.nxy.hasstools.utils

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

// ======================= 对外 API（WGS84 输入） =======================

/**
 * 计算平均分布圆 A 与高斯圆 G 的交集加权面积。
 *
 * 交集加权面积 = 高斯密度在交集内的积分，单位为概率质量。
 *
 * @param aLat 均匀圆 A 的中心纬度（WGS84，度）
 * @param aLon 均匀圆 A 的中心经度（WGS84，度）
 * @param aRadiusMeters 均匀圆 A 的半径（米）
 * @param gLat 高斯圆 G 的中心纬度（μ，WGS84，度）
 * @param gLon 高斯圆 G 的中心经度（μ，WGS84，度）
 * @param gRadiusMeters 高斯圆 G 的半径（米）
 * @param sigmaMeters 高斯圆 G 的标准差 σ（米），对应 68% 置信度
 * @param absTol 积分的绝对容差，默认为 1e-9
 * @return 交集加权面积（概率质量）
 */
fun intersectWeightedArea(
    aLat: Double, aLon: Double, aRadiusMeters: Double,
    gLat: Double, gLon: Double, gRadiusMeters: Double, sigmaMeters: Double,
    absTol: Double = 1e-9
): Double {
    require(aRadiusMeters >= 0 && gRadiusMeters >= 0 && sigmaMeters > 0)
    // 以高斯中心为局部投影参考，把两圆中心从经纬度(°)转到米制平面
    val (ax, ay) = toLocalMeters(aLat, aLon, gLat, gLon)
    val a = Circle(ax, ay, aRadiusMeters)
    val g = GaussianDisk(0.0, 0.0, sigmaMeters, gRadiusMeters) // 高斯中心作为原点
    return intersectWeightedArea(a, g, absTol)
}

/**
 * 计算平均分布圆的总面积。
 *
 * @param radiusMeters 圆的半径（米）
 * @return 圆的几何面积（平方米）
 */
fun uniformDiskAreaMeters(radiusMeters: Double): Double {
    require(radiusMeters >= 0)
    return Math.PI * radiusMeters * radiusMeters
}

/**
 * 计算高斯分布圆的总概率质量。
 *
 * 高斯分布圆的中心为 μ，总面积 = 高斯密度在自身圆内的积分。
 *
 * @param gaussianRadiusMeters 高斯圆的半径（米）
 * @param sigmaMeters 高斯分布的标准差 σ（米）
 * @return 高斯圆内的总概率质量
 */
fun gaussianDiskMassMeters(gaussianRadiusMeters: Double, sigmaMeters: Double): Double {
    require(gaussianRadiusMeters >= 0 && sigmaMeters > 0)
    val x = gaussianRadiusMeters * gaussianRadiusMeters / (2.0 * sigmaMeters * sigmaMeters)
    return 1.0 - exp(-x)
}

// ======================= 内部实现（私有） =======================

private data class Circle(val cx: Double, val cy: Double, val r: Double)
private data class GaussianDisk(val muX: Double, val muY: Double, val sigma: Double, val radius: Double)

/**
 * WGS84 小范围局部投影。
 *
 * 以 (refLat, refLon) 为原点，将经纬度坐标转换为米制平面坐标。
 *
 * @param lat 目标点纬度（WGS84，度）
 * @param lon 目标点经度（WGS84，度）
 * @param refLat 参考点纬度（WGS84，度）
 * @param refLon 参考点经度（WGS84，度）
 * @return 相对于参考点的平面坐标 (x, y)，单位为米
 */
private fun toLocalMeters(lat: Double, lon: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
    val R = 6378137.0
    val dLat = Math.toRadians(lat - refLat)
    val dLon = Math.toRadians(lon - refLon)
    val x = R * dLon * cos(Math.toRadians(refLat))
    val y = R * dLat
    return x to y
}

/**
 * 计算高斯密度在均匀圆与高斯圆交集 A∩G 内的积分。
 *
 * 高斯圆 G 以自身中心为原点进行计算。
 *
 * @param uniform 均匀分布圆
 * @param gaussian 高斯分布圆
 * @param absTol 积分的绝对容差
 * @return 交集内的加权面积（概率质量）
 */
private fun intersectWeightedArea(uniform: Circle, gaussian: GaussianDisk, absTol: Double): Double {
    val d = hypot(uniform.cx - gaussian.muX, uniform.cy - gaussian.muY)
    val rG = gaussian.radius
    val rU = uniform.r
    val invSigma2 = 1.0 / (gaussian.sigma * gaussian.sigma)

    if (d == 0.0) {
        val rCut = min(rG, rU)
        return 1.0 - exp(-rCut * rCut * 0.5 * invSigma2)
    }

    fun theta(r: Double): Double {
        if (r + d <= rU) return 2 * Math.PI
        if (r >= d + rU) return 0.0
        if (d >= r + rU) return 0.0
        val cosVal = ((r * r + d * d - rU * rU) / (2.0 * r * d)).coerceIn(-1.0, 1.0)
        return 2.0 * acos(cosVal)
    }

    fun integrand(r: Double): Double {
        val th = theta(r)
        if (th == 0.0) return 0.0
        return (th / (2.0 * Math.PI)) * invSigma2 * exp(-0.5 * r * r * invSigma2) * r
    }

    return adaptiveSimpson(0.0, rG, absTol, ::integrand).coerceIn(0.0, 1.0)
}

// ---------------- 自适应辛普森积分 ----------------

/**
 * 自适应辛普森积分法。
 *
 * 使用自适应辛普森法则计算函数在区间 [a, b] 上的定积分。
 *
 * @param a 积分下限
 * @param b 积分上限
 * @param eps 误差容限
 * @param f 被积函数
 * @return 积分结果
 */
private fun adaptiveSimpson(a: Double, b: Double, eps: Double, f: (Double) -> Double): Double {
    val fa = f(a)
    val fb = f(b)
    val fm = f(0.5 * (a + b))
    val S = simpson(a, b, fa, fb, fm)
    return asr(a, b, eps, S, fa, fb, fm, f)
}

/**
 * 自适应辛普森递归实现。
 *
 * 递归地细分区间，直到满足误差要求。
 *
 * @param a 积分下限
 * @param b 积分上限
 * @param eps 误差容限
 * @param whole 当前区间的辛普森积分值
 * @param fa 函数在 a 点的值
 * @param fb 函数在 b 点的值
 * @param fm 函数在中点的值
 * @param f 被积函数
 * @return 积分结果
 */
private fun asr(a: Double, b: Double, eps: Double, whole: Double, fa: Double, fb: Double, fm: Double, f: (Double) -> Double): Double {
    val m = 0.5 * (a + b)
    val lm = 0.5 * (a + m)
    val rm = 0.5 * (m + b)
    val flm = f(lm)
    val frm = f(rm)
    val left = simpson(a, m, fa, fm, flm)
    val right = simpson(m, b, fm, fb, frm)
    val delta = left + right - whole
    if (abs(delta) <= 15.0 * eps) return left + right + delta / 15.0
    return asr(a, m, eps / 2.0, left, fa, fm, flm, f) + asr(m, b, eps / 2.0, right, fm, fb, frm, f)
}

/**
 * 辛普森法则计算定积分。
 *
 * 使用辛普森 1/3 法则计算函数在区间 [a, b] 上的定积分近似值。
 *
 * @param a 积分下限
 * @param b 积分上限
 * @param fa 函数在 a 点的值
 * @param fb 函数在 b 点的值
 * @param fm 函数在中点的值
 * @return 积分近似值
 */
private fun simpson(a: Double, b: Double, fa: Double, fb: Double, fm: Double): Double {
    return (b - a) * (fa + 4.0 * fm + fb) / 6.0
}
