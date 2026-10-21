# amn_widgets

AMN's home-screen widgets: a weather widget and a city clock, built with Jetpack Glance.
The Flutter side holds the edit pages (`/widget/weather`, `/widget/clock`), which show
the real widget through the `amn/widget-preview` platform view and talk to Kotlin over the
`amn/widgets` method channel. See the root README for the layout and credits.
