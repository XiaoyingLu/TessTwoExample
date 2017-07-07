package xy.android.tesstwoexample;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int PICK_REQUEST_CODE = 100;
    private Button pickBtn;
    private ImageView pickImg;
    private static final String EXSTORAGE_PATH = String.format("%s/%s", Environment.getExternalStorageDirectory().toString(), "TessTwoExample/");
    private static final String TESSDATA_PATH = String.format("%s%s", EXSTORAGE_PATH, "tessdata/");
    private static final String TRAIN_LANG = "chi_sim";
    private static final String TRAINEDDATA = String.format("%s.traineddata", TRAIN_LANG);
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    private TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        verifyStoragePermissions(this);
        try {
            prepareTrainedFileIfNotExist();
        } catch (Exception e) {
//            Log.e(TAG, e.toString());

            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("Asset setup failed.")
                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }})
                    .show();
        }

        pickBtn = (Button) findViewById(R.id.btn_pick);
        pickImg = (ImageView) findViewById(R.id.iv_pick);
        text = (TextView) findViewById(R.id.tv_text);

        pickBtn.setOnClickListener(this);
    }

    public static void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareTrainedFileIfNotExist() throws Exception {

        String paths[] = {EXSTORAGE_PATH, EXSTORAGE_PATH + "tessdata"};
        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new Exception("创建文件夹失败");
                }
            }
        }

        String traineddata_path = String.format("%s%s", TESSDATA_PATH, TRAINEDDATA);

        if ( (new File(traineddata_path).exists()))
            return;

        try {
            InputStream in = getAssets().open(TRAINEDDATA);
            OutputStream out = new FileOutputStream(traineddata_path);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (IOException e) {
//            Log.e(TAG, e.toString());
            throw new Exception(e.getMessage());
        }
    }

    public static String getRealPathFromURI(Context context, Uri contentURI) {
        String result;
        Cursor cursor = context.getContentResolver().query(contentURI,
                new String[]{MediaStore.Images.ImageColumns.DATA},//
                null, null, null);
        if (cursor == null) result = contentURI.getPath();
        else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(index);
            cursor.close();
        }
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_REQUEST_CODE) {
                Uri source = data.getData();
                Bitmap bitmap = null;
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), source);
                    pickImg.setImageBitmap(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                recognizeText(getRealPathFromURI(getBaseContext(), source));
            }
        }
    }

    private void recognizeText(String path){
        // 获取Bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        // 图片旋转角度
        int rotate = 0;

        ExifInterface exif = null;
        try {
            exif = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 先获取当前图像的方向，判断是否需要旋转
        int imageOrientation = exif
                .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);

//        Log.i(TAG, "Current image orientation is " + imageOrientation);

        switch (imageOrientation)
        {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotate = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotate = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotate = 270;
                break;
            default:
                break;
        }

//        Log.i(TAG, "Current image need rotate: " + rotate);

        //获取当前图片的宽和高
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // 使用Matrix对图片进行处理
        Matrix mtx = new Matrix();
        mtx.preRotate(rotate);

        //旋转图片
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
//        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        bitmap =  ImageFilter.gray2Binary(bitmap);// 图片二值化
        bitmap =  ImageFilter.grayScaleImage(bitmap);// 图片灰度化

        // 开始调用Tess函数对图像进行识别
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
        baseApi.setRectangle(0, h - 200, w, h);
        baseApi.init(EXSTORAGE_PATH, TRAIN_LANG);
        baseApi.setImage(bitmap);

        // 获取返回值
        String recognizedText = baseApi.getUTF8Text();
//        baseApi.getRegions()
        text.setText(recognizedText);
        baseApi.end();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_pick:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
                startActivityForResult(intent, PICK_REQUEST_CODE);
                break;
            default:
        }
    }
}
