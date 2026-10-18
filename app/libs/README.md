# Bundled libraries

## Baidu LBS Android SDK

The positioning page uses the Baidu Map SDK, and the required files **are
bundled with this repository** so that the project builds out of the box:

```
app/libs/BaiduLBS_Android.jar
app/src/main/jniLibs/arm64-v8a/*.so
app/src/main/jniLibs/armeabi-v7a/*.so
```

The API key (`com.baidu.lbsapi.API_KEY` in `AndroidManifest.xml`) is tied to the
package name `com.ecut.pnav` and to the signing certificate used by the project
maintainers. **If you sign the app with your own key, the bundled API key will
not work** — obtain your own from <https://lbsyun.baidu.com/> (bind it to
`com.ecut.pnav` and your certificate SHA1) and replace the value in
`AndroidManifest.xml`.

### If you do not need the map

You can remove the Baidu dependency entirely: delete this folder's jar, delete
`app/src/main/jniLibs/`, and remove the Baidu-dependent code (`ui/Map/`, the
`MapFragment` entry in the navigation graph, and the two `SDKInitializer` calls
in `MyApplication`). This drops roughly 38 MB from the repository.
