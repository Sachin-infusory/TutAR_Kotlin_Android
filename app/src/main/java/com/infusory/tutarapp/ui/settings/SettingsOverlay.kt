package com.infusory.tutarapp.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.infusory.tutarapp.R

class SettingsPopup(private val context: Context) {

    private var dialog: Dialog? = null
    private val prefs = context.getSharedPreferences("whiteboard_settings", Context.MODE_PRIVATE)

    // Settings values - loaded from SharedPreferences
    private var showClock: Boolean
    private var aiAssistantEnabled: Boolean
    private var toolbarPosition: ToolbarPosition
    private var buttonSize: ButtonSize

    // UI References for updates
    private var toolbarPositionContainer: LinearLayout? = null
    private var radioGroup: RadioGroup? = null

    enum class ToolbarPosition {
        LEFT_SIDE, BOTH_SIDES, RIGHT_SIDE
    }

    enum class ButtonSize {
        SMALL, MEDIUM, LARGE
    }

    init {
        // Load saved settings
        showClock = prefs.getBoolean("show_clock", true)
        aiAssistantEnabled = prefs.getBoolean("ai_assistant_enabled", true)
        toolbarPosition = try {
            ToolbarPosition.valueOf(prefs.getString("toolbar_position", "BOTH_SIDES") ?: "BOTH_SIDES")
        } catch (e: Exception) {
            ToolbarPosition.BOTH_SIDES
        }
        buttonSize = try {
            ButtonSize.valueOf(prefs.getString("button_size", "SMALL") ?: "SMALL")
        } catch (e: Exception) {
            ButtonSize.SMALL
        }
    }

    fun show() {
        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val contentView = createSettingsView()
            setContentView(contentView)

            window?.setLayout(
                dpToPx(700),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            window?.attributes?.gravity = Gravity.CENTER
            window?.setDimAmount(0.6f)

            show()
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun createSettingsView(): ScrollView {
        return ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.parseColor("#F5F7FA"))
                elevation = dpToPx(8).toFloat()

                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(Color.parseColor("#F5F7FA"))
                }

                clipToOutline = true

                addView(createHeader())

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(24), 0, dpToPx(24), dpToPx(24))

                    addView(createSectionTitle("Customization"))
                    addView(createDivider())

                    addView(createToggleSetting(
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        iconBgColor = "#4FC3F7",
                        title = "Show Clock",
                        description = "Display clock in the whiteboard screen",
                        isActive = showClock,
                        activeLabel = "ACTIVE",
                        onToggle = { enabled ->
                            showClock = enabled
                            handleShowClockSetting(enabled)
                        }
                    ))

                    addView(createSpacer(16))

                    addView(createToggleSetting(
                        iconRes = android.R.drawable.ic_dialog_info,
                        iconBgColor = "#AB47BC",
                        title = "AI Assistant",
                        description = "Enable or disable AI features in your workspace",
                        isActive = aiAssistantEnabled,
                        activeLabel = "ON",
                        onToggle = { enabled ->
                            aiAssistantEnabled = enabled
                            handleAiAssistantSetting(enabled)
                        }
                    ))

                    addView(createSpacer(16))
                    addView(createToolbarPositionSetting())
                    addView(createSpacer(16))
                    addView(createButtonSizeSetting())
                })
            })
        }
    }

    private fun createHeader(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                setImageResource(android.R.drawable.ic_menu_revert)
                setColorFilter(Color.parseColor("#333333"))
                setOnClickListener { dismiss() }
            })

            addView(TextView(context).apply {
                text = "Settings"
                textSize = 20f
                setTextColor(Color.parseColor("#333333"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dpToPx(16)
                }
            })
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 22f
            setTextColor(Color.parseColor("#7B3FF2"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(16)
            }
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                bottomMargin = dpToPx(20)
            }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
    }

    private fun createToggleSetting(
        iconRes: Int,
        iconBgColor: String,
        title: String,
        description: String,
        isActive: Boolean,
        activeLabel: String,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        var currentState = isActive

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dpToPx(2).toFloat()

            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.WHITE)
            }

            addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))

                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.parseColor(iconBgColor))
                }

                addView(ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageResource(iconRes)
                    setColorFilter(Color.WHITE)
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dpToPx(16)
                    marginEnd = dpToPx(16)
                }

                addView(TextView(context).apply {
                    text = title
                    textSize = 16f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })

                addView(TextView(context).apply {
                    text = description
                    textSize = 13f
                    setTextColor(Color.parseColor("#757575"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                    }
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL

                val statusLabel = TextView(context).apply {
                    text = activeLabel
                    textSize = 12f
                    setTextColor(if (currentState) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dpToPx(8)
                    }
                }
                addView(statusLabel)

                val toggleSwitch = createToggleSwitch(currentState) { enabled ->
                    currentState = enabled
                    statusLabel.setTextColor(if (enabled) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))
                    onToggle(enabled)
                }
                addView(toggleSwitch)
            })
        }
    }

    private fun createToggleSwitch(isChecked: Boolean, onToggle: (Boolean) -> Unit): FrameLayout {
        var checked = isChecked

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(52), dpToPx(32))

            val track = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(if (checked) Color.parseColor("#4CAF50") else Color.parseColor("#BDBDBD"))
                }
            }
            addView(track)

            val thumb = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
                    gravity = if (checked) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
                    marginStart = dpToPx(4)
                    marginEnd = dpToPx(4)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                elevation = dpToPx(4).toFloat()
            }
            addView(thumb)

            setOnClickListener {
                checked = !checked

                track.background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(if (checked) Color.parseColor("#4CAF50") else Color.parseColor("#BDBDBD"))
                }

                thumb.layoutParams = (thumb.layoutParams as FrameLayout.LayoutParams).apply {
                    gravity = if (checked) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
                }

                onToggle(checked)
            }
        }
    }

    private fun createToolbarPositionSetting(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            setBackgroundColor(Color.WHITE)
            elevation = dpToPx(2).toFloat()

            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.WHITE)
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL

                addView(FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))

                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(12).toFloat()
                        setColor(Color.parseColor("#7B3FF2"))
                    }

                    addView(ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                            gravity = Gravity.CENTER
                        }
                        setImageResource(android.R.drawable.ic_menu_preferences)
                        setColorFilter(Color.WHITE)
                    })
                })

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart = dpToPx(16)
                    }

                    addView(TextView(context).apply {
                        text = "Toolbar Position"
                        textSize = 16f
                        setTextColor(Color.parseColor("#333333"))
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    })

                    addView(TextView(context).apply {
                        text = "Choose where to display the toolbar in whiteboard"
                        textSize = 13f
                        setTextColor(Color.parseColor("#757575"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dpToPx(4)
                        }
                    })
                })
            })

            toolbarPositionContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(16)
                }
                weightSum = 3f

                addView(createToolbarOption("Left Side", android.R.drawable.ic_media_rew, toolbarPosition == ToolbarPosition.LEFT_SIDE) {
                    toolbarPosition = ToolbarPosition.LEFT_SIDE
                    handleToolbarPositionSetting(ToolbarPosition.LEFT_SIDE)
                    refreshToolbarOptions()
                })

                addView(createSpacer(8))

                addView(createToolbarOption("Both Sides", android.R.drawable.ic_menu_sort_by_size, toolbarPosition == ToolbarPosition.BOTH_SIDES) {
                    toolbarPosition = ToolbarPosition.BOTH_SIDES
                    handleToolbarPositionSetting(ToolbarPosition.BOTH_SIDES)
                    refreshToolbarOptions()
                })

                addView(createSpacer(8))

                addView(createToolbarOption("Right Side", android.R.drawable.ic_media_ff, toolbarPosition == ToolbarPosition.RIGHT_SIDE) {
                    toolbarPosition = ToolbarPosition.RIGHT_SIDE
                    handleToolbarPositionSetting(ToolbarPosition.RIGHT_SIDE)
                    refreshToolbarOptions()
                })
            }
            addView(toolbarPositionContainer)
        }
    }

    private fun createToolbarOption(label: String, iconRes: Int, isSelected: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(dpToPx(16), dpToPx(20), dpToPx(16), dpToPx(20))
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(2), if (isSelected) Color.parseColor("#4FC3F7") else Color.parseColor("#E0E0E0"))
                setColor(if (isSelected) Color.parseColor("#E3F2FD") else Color.WHITE)
            }

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
                setImageResource(iconRes)
                setColorFilter(if (isSelected) Color.parseColor("#4FC3F7") else Color.parseColor("#757575"))
            })

            addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(if (isSelected) Color.parseColor("#4FC3F7") else Color.parseColor("#757575"))
                typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(8)
                }
            })

            setOnClickListener { onClick() }
        }
    }

    private fun refreshToolbarOptions() {
        toolbarPositionContainer?.removeAllViews()

        toolbarPositionContainer?.addView(createToolbarOption("Left Side", android.R.drawable.ic_media_rew, toolbarPosition == ToolbarPosition.LEFT_SIDE) {
            toolbarPosition = ToolbarPosition.LEFT_SIDE
            handleToolbarPositionSetting(ToolbarPosition.LEFT_SIDE)
            refreshToolbarOptions()
        })

        toolbarPositionContainer?.addView(createSpacer(8))

        toolbarPositionContainer?.addView(createToolbarOption("Both Sides", android.R.drawable.ic_menu_sort_by_size, toolbarPosition == ToolbarPosition.BOTH_SIDES) {
            toolbarPosition = ToolbarPosition.BOTH_SIDES
            handleToolbarPositionSetting(ToolbarPosition.BOTH_SIDES)
            refreshToolbarOptions()
        })

        toolbarPositionContainer?.addView(createSpacer(8))

        toolbarPositionContainer?.addView(createToolbarOption("Right Side", android.R.drawable.ic_media_ff, toolbarPosition == ToolbarPosition.RIGHT_SIDE) {
            toolbarPosition = ToolbarPosition.RIGHT_SIDE
            handleToolbarPositionSetting(ToolbarPosition.RIGHT_SIDE)
            refreshToolbarOptions()
        })
    }

    private fun createButtonSizeSetting(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dpToPx(2).toFloat()

            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.WHITE)
            }

            addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))

                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.parseColor("#00BCD4"))
                }

                addView(ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageResource(android.R.drawable.ic_menu_view)
                    setColorFilter(Color.WHITE)
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dpToPx(16)
                    marginEnd = dpToPx(16)
                }

                addView(TextView(context).apply {
                    text = "Button Size"
                    textSize = 16f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })

                addView(TextView(context).apply {
                    text = "Adjust toolbar button size"
                    textSize = 13f
                    setTextColor(Color.parseColor("#757575"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                    }
                })
            })

            addView(createRadioGroup())
        }
    }

    private fun createRadioGroup(): RadioGroup {
        return RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val smallRadio = createRadioButton("Small", buttonSize == ButtonSize.SMALL)
            val mediumRadio = createRadioButton("Medium", buttonSize == ButtonSize.MEDIUM)
            val largeRadio = createRadioButton("Large", buttonSize == ButtonSize.LARGE)

            addView(smallRadio)
            addView(mediumRadio)
            addView(largeRadio)

            setOnCheckedChangeListener { _, checkedId ->
                buttonSize = when (checkedId) {
                    smallRadio.id -> ButtonSize.SMALL
                    mediumRadio.id -> ButtonSize.MEDIUM
                    largeRadio.id -> ButtonSize.LARGE
                    else -> ButtonSize.SMALL
                }
                handleButtonSizeSetting(buttonSize)
            }

            radioGroup = this
        }
    }

    private fun createRadioButton(label: String, isChecked: Boolean): RadioButton {
        return RadioButton(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            this.isChecked = isChecked
            layoutParams = RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.WRAP_CONTENT,
                RadioGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8)
            }
        }
    }

    // Settings handlers - All functionality contained within this class
    private fun handleShowClockSetting(enabled: Boolean) {
        val message = if (enabled) "Clock enabled" else "Clock disabled"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        prefs.edit().putBoolean("show_clock", enabled).apply()

        // TODO: Implement actual clock widget show/hide
    }

    private fun handleAiAssistantSetting(enabled: Boolean) {
        val message = if (enabled) "AI Assistant enabled" else "AI Assistant disabled"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        prefs.edit().putBoolean("ai_assistant_enabled", enabled).apply()

        // TODO: Implement actual AI assistant enable/disable
    }

    private fun handleToolbarPositionSetting(position: ToolbarPosition) {
        if (context is AppCompatActivity) {
            val leftToolbar = context.findViewById<View>(R.id.left_toolbar)
            val rightToolbar = context.findViewById<View>(R.id.right_toolbar)

            when (position) {
                ToolbarPosition.LEFT_SIDE -> {
                    leftToolbar?.visibility = View.VISIBLE
                    rightToolbar?.visibility = View.GONE
                    Toast.makeText(context, "Toolbar: Left Side", Toast.LENGTH_SHORT).show()
                }
                ToolbarPosition.BOTH_SIDES -> {
                    leftToolbar?.visibility = View.VISIBLE
                    rightToolbar?.visibility = View.VISIBLE
                    Toast.makeText(context, "Toolbar: Both Sides", Toast.LENGTH_SHORT).show()
                }
                ToolbarPosition.RIGHT_SIDE -> {
                    leftToolbar?.visibility = View.GONE
                    rightToolbar?.visibility = View.VISIBLE
                    Toast.makeText(context, "Toolbar: Right Side", Toast.LENGTH_SHORT).show()
                }
            }
        }

        prefs.edit().putString("toolbar_position", position.name).apply()
    }

    private fun handleButtonSizeSetting(size: ButtonSize) {
        if (context is AppCompatActivity) {
            val leftToolbar = context.findViewById<View>(R.id.left_toolbar)
            val rightToolbar = context.findViewById<View>(R.id.right_toolbar)

            val buttonSize = when (size) {
                ButtonSize.SMALL -> 40
                ButtonSize.MEDIUM -> 56
                ButtonSize.LARGE -> 72
            }

            updateToolbarButtonSizes(leftToolbar, buttonSize)
            updateToolbarButtonSizes(rightToolbar, buttonSize)

            Toast.makeText(context, "Button size: ${size.name}", Toast.LENGTH_SHORT).show()
        }

        prefs.edit().putString("button_size", size.name).apply()
    }

    private fun updateToolbarButtonSizes(toolbar: View?, sizeDp: Int) {
        if (toolbar !is ViewGroup) return

        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()

        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is ImageButton) {
                child.layoutParams = child.layoutParams.apply {
                    width = sizePx
                    height = sizePx
                }
            } else if (child is ViewGroup) {
                updateToolbarButtonSizes(child, sizeDp)
            }
        }
        toolbar.requestLayout()
    }

    // Load and apply saved settings when needed
    fun applySavedSettings() {
        if (context is AppCompatActivity) {
            handleToolbarPositionSetting(toolbarPosition)
            handleButtonSizeSetting(buttonSize)
        }
    }

    private fun createSpacer(dp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(dp), dpToPx(dp))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}