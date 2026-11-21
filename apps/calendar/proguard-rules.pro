# Glance identifies a widget by its GlanceAppWidget class name: it stores the name per receiver
# and finds widget ids by it (getGlanceIds, updateAll). R8 merged AgendaWidget, MonthWidget and
# DateWidget into one class, so all three had the same name and updating the agenda also redrew
# the month widget as an agenda (Calendar 0.5.0). Keep each class, and its name stable across
# releases (the name is persisted on the device).
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
