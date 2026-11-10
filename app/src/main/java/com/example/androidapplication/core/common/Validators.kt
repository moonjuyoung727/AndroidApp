package com.example.androidapplication.core.common

/** 입력 검증. 문제가 없으면 null, 있으면 화면에 보여 줄 문구를 돌려준다. */
object Validators {
    const val EMAIL_MAX = 254
    const val NAME_MAX = 50
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64
    const val CAMERA_NAME_MAX = 15
    const val CAMERA_LOCATION_MAX = 30
    const val CAMERA_DESCRIPTION_MAX = 200
    const val EVENT_MEMO_MAX = 500
    const val LOCATION_DEFAULT = "미설정"

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val PASSWORD_CHARS = Regex("^[A-Za-z0-9!@#$%^&*]*$")
    private val CAMERA_NAME = Regex("^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 \\-_()]*$")
    private val PHONE = Regex("^[0-9]{9,11}$")

    fun email(value: String): String? = when {
        value.isBlank() -> "이메일을 입력해 주세요."
        value.length > EMAIL_MAX -> "이메일은 최대 ${EMAIL_MAX}자까지 입력할 수 있습니다."
        !EMAIL.matches(value) -> "올바른 이메일 형식이 아닙니다."
        else -> null
    }

    fun name(value: String): String? = when {
        value.isBlank() -> "이름을 입력해 주세요."
        value.length > NAME_MAX -> "이름은 최대 ${NAME_MAX}자까지 입력할 수 있습니다."
        else -> null
    }

    fun password(value: String): String? = when {
        value.isEmpty() -> "비밀번호를 입력해 주세요."
        value.length !in PASSWORD_MIN..PASSWORD_MAX -> "비밀번호는 ${PASSWORD_MIN}~${PASSWORD_MAX}자로 입력해 주세요."
        !PASSWORD_CHARS.matches(value) -> "비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)만 사용할 수 있습니다."
        else -> null
    }

    fun passwordConfirm(password: String, confirm: String): String? =
        if (password != confirm) "비밀번호가 일치하지 않습니다." else null

    /** 회원가입 입력 차단: 허용 외 문자를 지운다. */
    fun filterPasswordInput(value: String): String =
        value.filter { PASSWORD_CHARS.matches(it.toString()) }.take(PASSWORD_MAX)

    fun phone(value: String): String? =
        if (PHONE.matches(value)) null else "휴대폰 번호를 숫자만 입력해 주세요."

    fun verificationCode(value: String): String? =
        if (value.isBlank()) "인증 코드를 입력해 주세요." else null

    fun cameraMac(value: String, existingMacs: Collection<String>): String? {
        val mac = Mac.normalize(value)
        return when {
            mac.isEmpty() -> "MAC 주소를 입력해 주세요."
            mac.length != 12 -> "MAC 주소 형식이 올바르지 않습니다. (예: AA:BB:CC:DD:EE:FF)"
            existingMacs.any { Mac.normalize(it) == mac } -> "이미 등록된 MAC 주소입니다."
            else -> null
        }
    }

    fun cameraName(value: String): String? = when {
        value.isBlank() -> "카메라 이름을 입력해 주세요."
        value.length > CAMERA_NAME_MAX -> "카메라 이름은 최대 ${CAMERA_NAME_MAX}자까지 입력할 수 있습니다."
        !CAMERA_NAME.matches(value) -> "한글, 영문, 숫자, 공백, -_()만 사용할 수 있습니다."
        else -> null
    }

    fun cameraLocation(value: String): String? =
        if (value.length > CAMERA_LOCATION_MAX) "위치는 최대 ${CAMERA_LOCATION_MAX}자까지 입력할 수 있습니다." else null

    fun memo(value: String): String? =
        if (value.length > EVENT_MEMO_MAX) "메모는 최대 ${EVENT_MEMO_MAX}자까지 입력할 수 있습니다." else null
}
