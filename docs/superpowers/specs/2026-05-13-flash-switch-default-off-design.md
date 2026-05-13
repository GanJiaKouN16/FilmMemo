# Flash Switch Default Off

## Problem
The flash switch on the light metering page currently defaults to ON, but it should default to OFF. The `flashEnabled` variable is initialized to `false`, but the `SwitchMaterial` view's default checked state is not explicitly set, causing it to be ON on some devices/API levels.

## Solution
Make the flash switch default to OFF using two defensive measures:

1. **XML** (`fragment_lightmeter.xml`): Add `android:checked="false"` to the `SwitchMaterial` — makes the default explicit in the layout declaration.
2. **Kotlin** (`LightmeterFragment.kt`): Set `binding.switchFlash.isChecked = false` before attaching the `setOnCheckedChangeListener` — ensures the state is OFF regardless of theme/API quirks that might override the XML default.

## Files Changed
- `app/src/main/res/layout/fragment_lightmeter.xml` — one attribute addition
- `app/src/main/java/com/filmemo/ui/LightmeterFragment.kt` — one line addition

## Verification
- Flash switch shows unchecked when light metering page loads
- Flash content section (`layout_flash_content`) remains hidden
- `flashEnabled` variable remains `false` until user toggles the switch
