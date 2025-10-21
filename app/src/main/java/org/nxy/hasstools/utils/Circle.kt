package org.nxy.hasstools.utils

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

// ======================= 对外 API（WGS84 输入） =======================

/** 1) 平均分布圆 A 与高斯圆 G 的“交集加权面积”（= 高斯密度在交集内的积分，单位=概率质量） */
fun intersectWeightedArea(
    // A：均匀圆（WGS84 度 + 半径米）
    aLat: Double, aLon: Double, aRadiusMeters: Double,
    // G：高斯圆（中心=μ，WGS84 度 + 半径米 + σ米；σ=accuracy，对应68%）
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

/** 2) 平均分布圆的总面积（几何面积，单位 m²） */
fun uniformDiskAreaMeters(radiusMeters: Double): Double {
    require(radiusMeters >= 0)
    return Math.PI * radiusMeters * radiusMeters
}

/** 3) 高斯分布圆（中心=μ）的“总面积”（= 高斯密度在自身圆内的积分，单位=概率质量） */
fun gaussianDiskMassMeters(gaussianRadiusMeters: Double, sigmaMeters: Double): Double {
    require(gaussianRadiusMeters >= 0 && sigmaMeters > 0)
    val x = gaussianRadiusMeters * gaussianRadiusMeters / (2.0 * sigmaMeters * sigmaMeters)
    return 1.0 - exp(-x)
}

// ======================= 内部实现（私有） =======================

private data class Circle(val cx: Double, val cy: Double, val r: Double)
private data class GaussianDisk(val muX: Double, val muY: Double, val sigma: Double, val radius: Double)

/** WGS84 小范围局部投影：以 (refLat,refLon) 为原点，返回 (x,y) 米 */
private fun toLocalMeters(lat: Double, lon: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
    val R = 6378137.0
    val dLat = Math.toRadians(lat - refLat)
    val dLon = Math.toRadians(lon - refLon)
    val x = R * dLon * cos(Math.toRadians(refLat))
    val y = R * dLat
    return x to y
}

/** 高斯密度在 A∩G 的积分（G 以自身中心为原点） */
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

private fun adaptiveSimpson(a: Double, b: Double, eps: Double, f: (Double) -> Double): Double {
    val fa = f(a)
    val fb = f(b)
    val fm = f(0.5 * (a + b))
    val S = simpson(a, b, fa, fb, fm)
    return asr(a, b, eps, S, fa, fb, fm, f)
}

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

private fun simpson(a: Double, b: Double, fa: Double, fb: Double, fm: Double): Double {
    return (b - a) * (fa + 4.0 * fm + fb) / 6.0
}
