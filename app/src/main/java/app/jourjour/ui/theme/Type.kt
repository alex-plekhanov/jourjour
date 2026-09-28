package app.jourjour.ui.theme

import androidx.compose.material3.Typography

/**
 * Material's own scale, unaltered. The app has no typeface of its own and nothing to say
 * about the size of a title, so there is nothing here to decide.
 *
 * It is written down all the same, because the template this file came from overrode
 * `bodyLarge` with a `TextStyle(...)` built from scratch. Its numbers were Material's own to
 * the sp, so it looked like a no-op — but a hand-built `TextStyle` also leaves
 * `platformStyle` and `lineHeightStyle` unset, and Material sets both on every style it
 * ships: `includeFontPadding = false`, and a line height centred on the text rather than
 * hung above it. Body text was therefore laid out on different vertical metrics from every
 * other style in the app, and a text field's contents sat a little low in it.
 *
 * So: if a style is ever overridden here, copy it from `MaterialTheme.typography` and change
 * what needs changing. Never construct a `TextStyle` for it.
 */
val Typography = Typography()
