# Android UI design contract

This contract records the interface rules used for the passenger and rider apps. It is a design reference, not evidence of physical-device validation.

## Reference and product identity

The official Yango Google Play listing is used only as a structural reference for a map-first ride flow, a prominent destination action, compact ride-status surfaces, and progressive disclosure. DHW-inRide keeps its navy/orange identity, Chongwe pilot language, cash controls, rider approval safeguards, and original components and copy. No Yango artwork, brand assets, screenshots, or exact layouts are included.

Source: [Yango: taxi and delivery](https://play.google.com/store/apps/details?id=com.yandex.yango&hl=en)

## Interaction rules

- Passenger booking begins with one clear destination action over a map. Pickup and destination can be found by address search or by moving the map pin.
- Location controls describe their outcome. Search results show a name and address before selection.
- Rider requests never show a deadline unless the backend supplies and enforces an expiry time. When several requests exist, the selected request is visually and semantically explicit.
- Rider application is split into account, motorcycle, and review steps. Submitting an application does not imply approval or trip access.
- Notification permission is requested after its ride-status benefit is explained. Declining it does not block app access.
- Pilot availability, authorization, cash payment, and rider approval remain enforced by backend state. UI styling must not weaken those guards.

## Accessibility and adaptive layout

- Interactive targets must be at least 48 dp where custom controls are used. Material controls retain their platform defaults.
- Controls expose button, tab, or selection semantics; decorative glyphs do not become meaningless TalkBack announcements.
- Core labels use at least 12 sp and screens remain scrollable for larger text and short displays.
- Map surfaces always have a non-map textual way to select or understand a location.
- Physical validation must cover TalkBack, large font, portrait, landscape, a small Android screen, and both supported app roles.

Sources:

- [Accessibility defaults in Jetpack Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Semantics in Jetpack Compose](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Build adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)

## Evidence boundary

Automated unit tests and Android builds verify state transitions and compilation. They do not prove visual quality, touch ergonomics, screen-reader behavior, map rendering, keyboard behavior, or real-device notification delivery. Those remain device-rehearsal gates.
