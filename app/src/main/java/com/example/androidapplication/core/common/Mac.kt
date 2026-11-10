package com.example.androidapplication.core.common

/**
 * MAC 주소 형식.
 * 서버: 대문자, 콜론 없는 12자리 / 화면: AA:BB:CC:DD:EE:FF / device_id 기본값: "CAM-" + 끝 4자리
 */
object Mac {
    private val NON_HEX = Regex("[^0-9a-fA-F]")

    fun normalize(value: String?): String = value.orEmpty().replace(NON_HEX, "").uppercase()

    /** 12자리가 아니면 받은 값을 그대로 돌려준다. */
    fun format(value: String?): String {
        val hex = normalize(value)
        return if (hex.length == 12) hex.chunked(2).joinToString(":") else value.orEmpty()
    }

    fun deviceIdFrom(value: String?): String = "CAM-" + normalize(value).takeLast(4)

    /** 입력 중인 값을 대문자·콜론 형식으로 바꾼다. 12자리를 넘는 입력은 버린다. */
    fun formatInput(raw: String): String = normalize(raw).take(12).chunked(2).joinToString(":")

    fun isComplete(value: String?): Boolean = normalize(value).length == 12
}
