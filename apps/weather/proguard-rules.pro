# Kept as in Calendar (see apps/calendar/proguard-rules.pro for the R8 problems they prevent).
# Glance identifies a widget by its GlanceAppWidget class name: it stores the name per receiver
# and finds widget ids by it (getGlanceIds, updateAll). R8 merged AgendaWidget, MonthWidget and
# DateWidget into one class, so all three had the same name and updating the agenda also redrew
# the month widget as an agenda (Calendar 0.5.0). Keep each class, and its name stable across
# releases (the name is persisted on the device).
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Glance creates an action callback by reflection (from its class name, with its no-argument
# constructor) when it's tapped. R8 dropped MonthNavigation's constructor as unused, so the month
# widget's arrows did nothing in 0.5.0 and 0.5.1.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
