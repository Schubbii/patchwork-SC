package de.phonezone

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Auswahl der Apps, die im Modus "Nur Apps" gesperrt werden. */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var selected: MutableSet<String>
    private lateinit var apps: List<ResolveInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        selected = Prefs.blockedApps(this).toMutableSet()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        findViewById<ListView>(R.id.app_list).apply {
            adapter = AppAdapter()
            setOnItemClickListener { _, row, position, _ ->
                val pkg = apps[position].activityInfo.packageName
                if (!selected.remove(pkg)) selected += pkg
                row.findViewById<CheckBox>(R.id.app_check).isChecked = pkg in selected
            }
        }
        findViewById<Button>(R.id.done_button).setOnClickListener { finish() }
    }

    override fun onPause() {
        super.onPause()
        Prefs.setBlockedApps(this, selected)
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            row.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.loadIcon(packageManager))
            row.findViewById<TextView>(R.id.app_name).text = app.loadLabel(packageManager)
            row.findViewById<CheckBox>(R.id.app_check).isChecked = app.activityInfo.packageName in selected
            return row
        }
    }
}
