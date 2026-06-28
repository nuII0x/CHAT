# Launcher icon source

Place the improved PNG source here as:

```text
ic_launcher_foreground.png
```

Use a transparent PNG, ideally 512 x 512 px or larger. Replace:

```text
app/src/main/res/drawable-v24/ic_launcher_foreground.png
```

The adaptive launcher icon already points to:

```text
@drawable/ic_launcher_foreground_adaptive
```

That inset drawable points to `@drawable/ic_launcher_foreground`. On Android versions that use adaptive icons, the PNG in `drawable-v24` is selected for that name, so replacing it updates the launcher foreground.
