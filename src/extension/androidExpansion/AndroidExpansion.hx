package extension.androidExpansion;

#if android
import lime.system.JNI;
#end

class AndroidExpansion {

    public static var version:Int;

    static var _init:Dynamic;
    static var _expansionFilesDelivered:Dynamic;
    static var _startDownloadServiceIfRequired:Dynamic;
    static var _getMainFile:Dynamic;
    static var _getPackageName:Dynamic;
    static var _getLocalStoragePath:Dynamic;
    static var _findBestPathToUnzip:Dynamic;
    static var _setVersion:Dynamic;
    static var _setSpaceNeeded:Dynamic;
    static var _getAPKVersion:Dynamic;
    static var _setBytes:Dynamic;
    static var _setKey:Dynamic;
    static var _setSalt:Dynamic;
    static var _checkPermissions:Dynamic;
    static var _hasExternalStoragePermissions:Dynamic;
    static var _askExternalStoragePermissions:Dynamic;
    static var _overallTotal:Dynamic;

    public static function init(obj:Dynamic):Void {
        initJNI();
        _init(obj);
    }

    public static function setVersion(v:Int):Void {
        initJNI();
        version = v;
        _setVersion(v);
    }

    public static function setSpaceNeeded(v:Int):Void {
        initJNI();
        _setSpaceNeeded(v);
    }

    public static function getAPKVersion():Int {
        initJNI();
        return _getAPKVersion();
    }

    public static function setBytes(v:Float):Void {
        initJNI();
        _setBytes(v);
    }

    public static function setKey(v:String):Void {
        initJNI();
        _setKey(v);
    }

    public static function setSalt(v:Array<Int>):Void {
        initJNI();
        _setSalt(v);
    }

    public static function expansionFilesDelivered():Bool {
        initJNI();
        if(_expansionFilesDelivered() == 1)
            return true;
        return false;
    }

    public static function startDownloadServiceIfRequired():Int {
        initJNI();
        return _startDownloadServiceIfRequired();
    }

    public static function getMainFile():String {
        initJNI();
        return _getMainFile()[0];
    }

    public static function getPackageName():String {
        initJNI();
        return _getPackageName();
    }

    public static function getLocalStoragePath():String {
        initJNI();
        return _getLocalStoragePath();
    }

    public static function findBestPathToUnzip():String {
        initJNI();
        return _findBestPathToUnzip();
    }

    public static function askExternalStoragePermissions(rationale:String):Void {
        initJNI();
        _askExternalStoragePermissions(rationale);
    }

    public static function hasExternalStoragePermissions():Bool {
        initJNI();
        return _hasExternalStoragePermissions() == 0 ? true : false;
    }

    public static function getOverallTotal():Int {
        initJNI();
        return _overallTotal();
    }

    private static function initJNI():Void {
        if(_init == null) {
            #if android
            _init = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "init", "(Lorg/haxe/lime/HaxeObject;)V");
            _expansionFilesDelivered = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "expansionFilesDelivered", "()I");
            _startDownloadServiceIfRequired = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "startDownloadServiceIfRequired", "()I");
            _getMainFile = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "getMainFile", "()Ljava/lang/String;");

            _getPackageName = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "getPackageName", "()Ljava/lang/String;");
            _getLocalStoragePath = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "getLocalStoragePath", "()Ljava/lang/String;");
            _findBestPathToUnzip = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "findBestPathToUnzip", "()Ljava/lang/String;");

            _setVersion = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "setVersion", "(I)V");
            _setSpaceNeeded = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "setSpaceNeeded", "(I)V");
            _getAPKVersion = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "getAPKVersion", "()I");
            _setBytes = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "setBytes", "(J)V");
            _setKey = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "setKey", "(Ljava/lang/String;)V");
            _setSalt = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "setSalt", "([B)V");

            _askExternalStoragePermissions = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "askExternalStoragePermissions", "(Ljava/lang/String;)V");
            _hasExternalStoragePermissions = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "hasExternalStoragePermissions", "()I");
            _overallTotal = JNI.createStaticMethod("com/thomasuster/androidExpansion/Expansion", "overallTotal", "()J");
            #end
        }
    }
}