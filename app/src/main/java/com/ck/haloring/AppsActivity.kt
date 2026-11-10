package com.ck.haloring

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Pick which apps show a ring, and give any app its own colour. */
class AppsActivity : Activity() {

    private class Row(val pkg: String, val label: String)

    private class Holder(
        val icon: ImageView,
        val name: TextView,
        val dot: View,
        val check: CheckBox
    )

    private val all = ArrayList<Row>()
    private val shown = ArrayList<Row>()
    private val icons = HashMap<String, Drawable?>()
    private var selected = HashSet<String>()
    private var filter = ""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: LinearLayout
            val h: Holder
            if (convertView == null) {
                view = LinearLayout(this@AppsActivity)
                view.orientation = LinearLayout.HORIZONTAL
                view.gravity = Gravity.CENTER_VERTICAL
                view.setPadding(dp(16), dp(8), dp(12), dp(8))

                val icon = ImageView(this@AppsActivity)
                view.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

                val name = TextView(this@AppsActivity)
                name.textSize = 16f
                name.setPadding(dp(14), 0, dp(8), 0)
                view.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                val dot = View(this@AppsActivity)
                val dotLp = LinearLayout.LayoutParams(dp(30), dp(30))
                dotLp.setMargins(dp(4), 0, dp(10), 0)
                view.addView(dot, dotLp)

                val check = CheckBox(this@AppsActivity)
                view.addView(check)

                h = Holder(icon, name, dot, check)
                view.tag = h
            } else {
                view = convertView as LinearLayout
                h = view.tag as Holder
            }

            val row = shown[position]
            h.name.text = row.label

            if (!icons.containsKey(row.pkg)) {
                icons[row.pkg] = try {
                    packageManager.getApplicationIcon(row.pkg)
                } catch (e: Exception) {
                    null
                }
            }
            h.icon.setImageDrawable(icons[row.pkg])

            val custom = Prefs.appColor(this@AppsActivity, row.pkg)
            h.dot.background =
                if (custom == null) ColorPicker.emptyDot(dp(2)) else ColorPicker.dot(custom, dp(1))
            h.dot.setOnClickListener {
                ColorPicker.show(
                    this@AppsActivity, row.label,
                    custom ?: Prefs.sp(this@AppsActivity).getInt(Prefs.COLOR, Prefs.DEF_COLOR),
                    true
                ) { picked ->
                    Prefs.setAppColor(this@AppsActivity, row.pkg, picked)
                    notifyDataSetChanged()
                    if (picked != null) RingService.instance?.preview(picked)
                }
            }

            h.check.isChecked = selected.contains(row.pkg)
            val toggle = View.OnClickListener {
                if (selected.contains(row.pkg)) selected.remove(row.pkg) else selected.add(row.pkg)
                Prefs.saveSelectedApps(this@AppsActivity, selected)
                notifyDataSetChanged()
            }
            h.check.setOnClickListener(toggle)
            view.setOnClickListener(toggle)

            return view
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = Prefs.selectedApps(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val help = TextView(this)
        help.text = "Tick the apps that should show a ring (used when \"All apps\" is off). " +
            "Tap an app's circle to give it its own colour; that works in both modes."
        help.setPadding(dp(16), dp(12), dp(16), dp(4))
        root.addView(help)

        val search = EditText(this)
        search.hint = "Search apps"
        search.setSingleLine(true)
        val searchLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        searchLp.setMargins(dp(12), 0, dp(12), 0)
        root.addView(search, searchLp)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
            }
        })

        val list = ListView(this)
        list.adapter = adapter
        root.addView(
            list,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        Thread {
            val found = ArrayList<Row>()
            val seen = HashSet<String>()
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                for (ri in packageManager.queryIntentActivities(intent, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName) continue
                    if (!seen.add(pkg)) continue
                    found.add(Row(pkg, ri.loadLabel(packageManager).toString()))
                }
            } catch (e: Exception) {
            }
            // Ticked apps first, then A to Z.
            val ticked = HashSet(selected)
            found.sortWith(
                compareBy<Row> { if (ticked.contains(it.pkg)) 0 else 1 }
                    .thenBy { it.label.lowercase() }
            )
            runOnUiThread {
                all.clear()
                all.addAll(found)
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        shown.clear()
        if (filter.isEmpty()) {
            shown.addAll(all)
        } else {
            for (r in all) {
                if (r.label.lowercase().contains(filter)) shown.add(r)
            }
        }
        adapter.notifyDataSetChanged()
    }
}
