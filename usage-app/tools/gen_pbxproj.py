#!/usr/bin/env python3
"""ClaudeUsage.xcodeproj/project.pbxproj 를 만든다 (맥에 xcodegen 없이 바로 열리게 커밋하는 파일).
파일을 더하면 아래 목록에 넣고 `python3 usage-app/tools/gen_pbxproj.py` 를 다시 돌린다. ID 는 이름에서 정해져 늘 같다."""
import hashlib, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEAM = "252W43H2KE"
APP, EXT = "ClaudeUsage", "ClaudeUsageWidgets"
APP_SRC = ["UsageApp/App.swift", "UsageShared/Usage.swift", "UsageWidgets/Views.swift"]
EXT_SRC = ["UsageWidgets/Widgets.swift", "UsageShared/Usage.swift", "UsageWidgets/Views.swift"]
APP_RES = ["UsageApp/Assets.xcassets", "UsageWidgets/Assets.xcassets", "UsageShared/Localizable.xcstrings"]
EXT_RES = ["UsageWidgets/Assets.xcassets", "UsageShared/Localizable.xcstrings"]
OTHER = ["UsageApp/Info.plist", "UsageApp/UsageApp.entitlements", "UsageWidgets/Info.plist", "UsageWidgets/UsageWidgets.entitlements"]


def i(*k):
    return hashlib.md5("/".join(k).encode()).hexdigest()[:24].upper()


def ftype(p):
    return {".swift": "sourcecode.swift", ".xcassets": "folder.assetcatalog", ".plist": "text.plist.xml", ".entitlements": "text.plist.entitlements", ".xcstrings": "text.json.xcstrings"}[os.path.splitext(p)[1]]


files = sorted(set(APP_SRC + EXT_SRC + APP_RES + EXT_RES + OTHER))
groups = sorted({f.split("/")[0] for f in files})
o = []
w = o.append
w("// !$*UTF8*$!\n{\n\tarchiveVersion = 1;\n\tclasses = {\n\t};\n\tobjectVersion = 77;\n\tobjects = {\n")

w("/* Begin PBXBuildFile section */\n")
for t, lst, ph in ((APP, APP_SRC, "Sources"), (EXT, EXT_SRC, "Sources"), (APP, APP_RES, "Resources"), (EXT, EXT_RES, "Resources")):
    for f in lst:
        n = os.path.basename(f)
        w(f"\t\t{i('bf', t, f)} /* {n} in {ph} */ = {{isa = PBXBuildFile; fileRef = {i('fr', f)} /* {n} */; }};\n")
w(f"\t\t{i('bf', 'embed')} /* {EXT}.appex in Embed Foundation Extensions */ = {{isa = PBXBuildFile; fileRef = {i('prod', EXT)} /* {EXT}.appex */; settings = {{ATTRIBUTES = (RemoveHeadersOnCopy, ); }}; }};\n")
w("/* End PBXBuildFile section */\n\n")

w("/* Begin PBXContainerItemProxy section */\n")
w(f"\t\t{i('proxy')} /* PBXContainerItemProxy */ = {{\n\t\t\tisa = PBXContainerItemProxy;\n\t\t\tcontainerPortal = {i('project')} /* Project object */;\n\t\t\tproxyType = 1;\n\t\t\tremoteGlobalIDString = {i('target', EXT)};\n\t\t\tremoteInfo = {EXT};\n\t\t}};\n")
w("/* End PBXContainerItemProxy section */\n\n")

w("/* Begin PBXCopyFilesBuildPhase section */\n")
w(f"\t\t{i('embedphase')} /* Embed Foundation Extensions */ = {{\n\t\t\tisa = PBXCopyFilesBuildPhase;\n\t\t\tbuildActionMask = 2147483647;\n\t\t\tdstPath = \"\";\n\t\t\tdstSubfolderSpec = 13;\n\t\t\tfiles = (\n\t\t\t\t{i('bf', 'embed')} /* {EXT}.appex in Embed Foundation Extensions */,\n\t\t\t);\n\t\t\tname = \"Embed Foundation Extensions\";\n\t\t\trunOnlyForDeploymentPostprocessing = 0;\n\t\t}};\n")
w("/* End PBXCopyFilesBuildPhase section */\n\n")

w("/* Begin PBXFileReference section */\n")
for f in files:
    n = os.path.basename(f)
    w(f"\t\t{i('fr', f)} /* {n} */ = {{isa = PBXFileReference; lastKnownFileType = {ftype(f)}; path = {n}; sourceTree = \"<group>\"; }};\n")
w(f"\t\t{i('prod', APP)} /* {APP}.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = {APP}.app; sourceTree = BUILT_PRODUCTS_DIR; }};\n")
w(f"\t\t{i('prod', EXT)} /* {EXT}.appex */ = {{isa = PBXFileReference; explicitFileType = \"wrapper.app-extension\"; includeInIndex = 0; path = {EXT}.appex; sourceTree = BUILT_PRODUCTS_DIR; }};\n")
w("/* End PBXFileReference section */\n\n")

w("/* Begin PBXGroup section */\n")
w(f"\t\t{i('main')} = {{\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (\n")
for g in groups:
    w(f"\t\t\t\t{i('group', g)} /* {g} */,\n")
w(f"\t\t\t\t{i('products')} /* Products */,\n\t\t\t);\n\t\t\tsourceTree = \"<group>\";\n\t\t}};\n")
for g in groups:
    w(f"\t\t{i('group', g)} /* {g} */ = {{\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (\n")
    for f in files:
        if f.split("/")[0] == g:
            w(f"\t\t\t\t{i('fr', f)} /* {os.path.basename(f)} */,\n")
    w(f"\t\t\t);\n\t\t\tpath = {g};\n\t\t\tsourceTree = \"<group>\";\n\t\t}};\n")
w(f"\t\t{i('products')} /* Products */ = {{\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (\n\t\t\t\t{i('prod', APP)} /* {APP}.app */,\n\t\t\t\t{i('prod', EXT)} /* {EXT}.appex */,\n\t\t\t);\n\t\t\tname = Products;\n\t\t\tsourceTree = \"<group>\";\n\t\t}};\n")
w("/* End PBXGroup section */\n\n")

w("/* Begin PBXNativeTarget section */\n")
for t, kind, phases, deps in ((APP, "com.apple.product-type.application", ["Sources", "Resources", "Embed"], True), (EXT, "com.apple.product-type.app-extension", ["Sources", "Resources"], False)):
    prod = f"{t}.app" if t == APP else f"{t}.appex"
    w(f"\t\t{i('target', t)} /* {t} */ = {{\n\t\t\tisa = PBXNativeTarget;\n\t\t\tbuildConfigurationList = {i('cl', t)} /* Build configuration list for PBXNativeTarget \"{t}\" */;\n\t\t\tbuildPhases = (\n")
    for p in phases:
        if p == "Embed":
            w(f"\t\t\t\t{i('embedphase')} /* Embed Foundation Extensions */,\n")
        else:
            w(f"\t\t\t\t{i('phase', t, p)} /* {p} */,\n")
    w("\t\t\t);\n\t\t\tbuildRules = (\n\t\t\t);\n\t\t\tdependencies = (\n")
    if deps:
        w(f"\t\t\t\t{i('dep')} /* PBXTargetDependency */,\n")
    w(f"\t\t\t);\n\t\t\tname = {t};\n\t\t\tpackageProductDependencies = (\n\t\t\t);\n\t\t\tproductName = {t};\n\t\t\tproductReference = {i('prod', t)} /* {prod} */;\n\t\t\tproductType = \"{kind}\";\n\t\t}};\n")
w("/* End PBXNativeTarget section */\n\n")

w("/* Begin PBXProject section */\n")
w(f"\t\t{i('project')} /* Project object */ = {{\n\t\t\tisa = PBXProject;\n\t\t\tattributes = {{\n\t\t\t\tBuildIndependentTargetsInParallel = YES;\n\t\t\t\tLastUpgradeCheck = 2600;\n\t\t\t\tTargetAttributes = {{\n")
for t in (APP, EXT):
    w(f"\t\t\t\t\t{i('target', t)} = {{\n\t\t\t\t\t\tDevelopmentTeam = {TEAM};\n\t\t\t\t\t\tProvisioningStyle = Automatic;\n\t\t\t\t\t}};\n")
w(f"\t\t\t\t}};\n\t\t\t}};\n\t\t\tbuildConfigurationList = {i('cl', 'project')} /* Build configuration list for PBXProject \"{APP}\" */;\n\t\t\tdevelopmentRegion = ko;\n\t\t\thasScannedForEncodings = 0;\n\t\t\tknownRegions = (\n\t\t\t\tBase,\n\t\t\t\ten,\n\t\t\t\tko,\n\t\t\t);\n\t\t\tmainGroup = {i('main')};\n\t\t\tminimizedProjectReferenceProxies = 1;\n\t\t\tpreferredProjectObjectVersion = 77;\n\t\t\tproductRefGroup = {i('products')} /* Products */;\n\t\t\tprojectDirPath = \"\";\n\t\t\tprojectRoot = \"\";\n\t\t\ttargets = (\n\t\t\t\t{i('target', APP)} /* {APP} */,\n\t\t\t\t{i('target', EXT)} /* {EXT} */,\n\t\t\t);\n\t\t}};\n")
w("/* End PBXProject section */\n\n")

for ph, lists in (("Resources", ((APP, APP_RES), (EXT, EXT_RES))), ("Sources", ((APP, APP_SRC), (EXT, EXT_SRC)))):
    w(f"/* Begin PBX{ph}BuildPhase section */\n")
    for t, lst in lists:
        w(f"\t\t{i('phase', t, ph)} /* {ph} */ = {{\n\t\t\tisa = PBX{ph}BuildPhase;\n\t\t\tbuildActionMask = 2147483647;\n\t\t\tfiles = (\n")
        for f in lst:
            w(f"\t\t\t\t{i('bf', t, f)} /* {os.path.basename(f)} in {ph} */,\n")
        w("\t\t\t);\n\t\t\trunOnlyForDeploymentPostprocessing = 0;\n\t\t};\n")
    w(f"/* End PBX{ph}BuildPhase section */\n\n")

w("/* Begin PBXTargetDependency section */\n")
w(f"\t\t{i('dep')} /* PBXTargetDependency */ = {{\n\t\t\tisa = PBXTargetDependency;\n\t\t\ttarget = {i('target', EXT)} /* {EXT} */;\n\t\t\ttargetProxy = {i('proxy')} /* PBXContainerItemProxy */;\n\t\t}};\n")
w("/* End PBXTargetDependency section */\n\n")

COMMON = dict(ALWAYS_SEARCH_USER_PATHS="NO", CLANG_ENABLE_MODULES="YES", CLANG_ENABLE_OBJC_ARC="YES", CODE_SIGN_STYLE="Automatic",
              COPY_PHASE_STRIP="NO", CURRENT_PROJECT_VERSION="1", DEVELOPMENT_TEAM=TEAM, ENABLE_STRICT_OBJC_MSGSEND="YES",
              GCC_C_LANGUAGE_STANDARD="gnu11", IPHONEOS_DEPLOYMENT_TARGET="17.0", MARKETING_VERSION="1.0", PRODUCT_NAME='"$(TARGET_NAME)"',
              SDKROOT="iphoneos", SWIFT_EMIT_LOC_STRINGS="YES", LOCALIZATION_PREFERS_STRING_CATALOGS="YES", SWIFT_VERSION="5.0", TARGETED_DEVICE_FAMILY="1")
CFG = {
    ("project", "Debug"): {**COMMON, "DEBUG_INFORMATION_FORMAT": "dwarf", "ENABLE_TESTABILITY": "YES", "GCC_OPTIMIZATION_LEVEL": "0", "ONLY_ACTIVE_ARCH": "YES",
                           "SWIFT_ACTIVE_COMPILATION_CONDITIONS": "DEBUG", "SWIFT_OPTIMIZATION_LEVEL": '"-Onone"'},
    ("project", "Release"): {**COMMON, "DEBUG_INFORMATION_FORMAT": '"dwarf-with-dsym"', "ENABLE_NS_ASSERTIONS": "NO", "SWIFT_COMPILATION_MODE": "wholemodule",
                             "SWIFT_OPTIMIZATION_LEVEL": '"-O"'},
}
for c in ("Debug", "Release"):
    CFG[(APP, c)] = {"ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon", "CODE_SIGN_ENTITLEMENTS": "UsageApp/UsageApp.entitlements", "INFOPLIST_FILE": "UsageApp/Info.plist",
                     "LD_RUNPATH_SEARCH_PATHS": '("$(inherited)", "@executable_path/Frameworks", )', "PRODUCT_BUNDLE_IDENTIFIER": "com.naeilo.usage"}
    CFG[(EXT, c)] = {"CODE_SIGN_ENTITLEMENTS": "UsageWidgets/UsageWidgets.entitlements", "INFOPLIST_FILE": "UsageWidgets/Info.plist", "SKIP_INSTALL": "YES",
                     "LD_RUNPATH_SEARCH_PATHS": '("$(inherited)", "@executable_path/Frameworks", "@executable_path/../../Frameworks", )',
                     "PRODUCT_BUNDLE_IDENTIFIER": "com.naeilo.usage.widgets"}
w("/* Begin XCBuildConfiguration section */\n")
for (t, c), s in CFG.items():
    w(f"\t\t{i('cfg', t, c)} /* {c} */ = {{\n\t\t\tisa = XCBuildConfiguration;\n\t\t\tbuildSettings = {{\n")
    for k in sorted(s):
        w(f"\t\t\t\t{k} = {s[k]};\n")
    w(f"\t\t\t}};\n\t\t\tname = {c};\n\t\t}};\n")
w("/* End XCBuildConfiguration section */\n\n")

w("/* Begin XCConfigurationList section */\n")
for t in ("project", APP, EXT):
    label = f'PBXProject "{APP}"' if t == "project" else f'PBXNativeTarget "{t}"'
    w(f"\t\t{i('cl', t)} /* Build configuration list for {label} */ = {{\n\t\t\tisa = XCConfigurationList;\n\t\t\tbuildConfigurations = (\n\t\t\t\t{i('cfg', t, 'Debug')} /* Debug */,\n\t\t\t\t{i('cfg', t, 'Release')} /* Release */,\n\t\t\t);\n\t\t\tdefaultConfigurationIsVisible = 0;\n\t\t\tdefaultConfigurationName = Debug;\n\t\t}};\n")
w("/* End XCConfigurationList section */\n")
w(f"\t}};\n\trootObject = {i('project')} /* Project object */;\n}}\n")

out = os.path.join(ROOT, f"{APP}.xcodeproj", "project.pbxproj")
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, "w").write("".join(o))
print(out)
