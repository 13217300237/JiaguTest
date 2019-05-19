package com.example.shell;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.shell.tools.AES;
import com.example.shell.tools.ClassLoaderHookHelper;
import com.example.shell.tools.Zip;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.util.Objects;

public class MyAppMain extends Application {
    private static final String TAG = "shellModule";
    private File unzipSavedPath;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        init();
    }

    private boolean checkFirstRunning() {
        String[] dexArr = unzipSavedPath.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("_") && name.endsWith(".dex");
            }
        });
        return dexArr.length <= 0;//没有dex就是第一次运行
    }

    private long c;

    private void init() {
        c = System.currentTimeMillis();
        //我们拿到了之前的
        //首先，我们的终极目标，是得到 base.apk里面的所有dex文件，遍历所有以_开头的dex，将他们进行AES解密，然后输出到本地，
        // 这样，我们就得到了N个源dex的原有内容，脱壳完成
        // 既然我们拿到了源dex，那么，利用multiDex热修复技术，可以让classLoader去优先加载这些类

        String installApkPath = getPackageResourcePath();//获得安装包的路径 :data/app/com.example.administrator.jiagutest-2/base.apk
        // 我就可以把它解压到app的私有目录
        unzipSavedPath = new File(getFilesDir().getParent() + "/fake");//解压之后的存放路径
        if (!unzipSavedPath.exists()) {
            unzipSavedPath.mkdirs();
        }

        boolean ifFirst = checkFirstRunning();
        Log.d(TAG, "1:开始:" + installApkPath + "\n" + unzipSavedPath);


        //首次运行，必须解压，解密，生成解密之后的若干dex
        if (ifFirst) {
            Log.d(TAG, "首次运行，必须全部解压且解密");
            AES.init(AES.DEFAULT_PWD);
            Zip.unZip(new File(installApkPath), unzipSavedPath);
            String[] tempDexArr = unzipSavedPath.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("_") && name.endsWith(".dex");
                }
            });
            //那么我现在应该可以找到其中的所有dex,
            for (String dex : tempDexArr) {
                byte[] bytes = getFullBytes(new File(unzipSavedPath + "/" + dex));//拿到所有的byte
                byte[] byteDecrypted = AES.decrypt(bytes);//已解密的所有byte
                BufferedOutputStream bos = null;
                try {
                    bos = new BufferedOutputStream(new FileOutputStream(unzipSavedPath + "/" + dex));
                    bos.write(byteDecrypted);
                    bos.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        Objects.requireNonNull(bos).close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            Log.d(TAG, "2: 所有源dex已解密");
        } else {
            Log.d(TAG, "非首次运行，直接使用已经解密的源dex");
        }

        //获得所有的源dex
        String[] dexArr = unzipSavedPath.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("_") && name.endsWith(".dex");
            }
        });
        //每次运行，必须对解密之后的若干dex进行 multiDex 优先加载
        Log.d(TAG, "3:准备遍历````");
        for (String dex : dexArr) {
            String path = unzipSavedPath.getAbsolutePath() + "/" + dex;
            Log.d(TAG, "3: 遍历 :" + path);
            try {
                File fixFile = new File(path);
                ClassLoaderHookHelper.hook(this, fixFile);//这里热修复，在Element[]前列，插入了若干dex，优先加载
                // 那么就会优先加载这些dex

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "application启动结束,init耗时 " + (System.currentTimeMillis() - c) + ":MS");
    }

    /**
     * 获取一个文件的全部字节序列
     *
     * @param file
     * @return
     */
    private static byte[] getFullBytes(File file) {
        byte[] buf = null;
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");

            buf = new byte[(int) file.length()];
            raf.readFully(buf);
            raf.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return buf;
    }
}
