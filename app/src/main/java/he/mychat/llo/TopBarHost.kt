package he.mychat.llo

interface TopBarHost {
    fun configureTopBar(
        title: CharSequence,
        showLeft: Boolean,
        leftIcon: Int = R.drawable.ic_back,
        onLeft: (() -> Unit)? = null,
        showRight: Boolean = false,
        rightIcon: Int = R.drawable.ic_settings,
        onRight: (() -> Unit)? = null
    )

    fun setSubtitle(text: CharSequence?) {
        // 默认不处理
    }
}