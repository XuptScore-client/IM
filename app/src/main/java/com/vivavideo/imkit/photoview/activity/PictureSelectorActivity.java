package com.vivavideo.imkit.photoview.activity;

import com.vivavideo.imkit.R;
import com.vivavideo.imkit.activity.AlbumBitmapCacheHelper;
import com.vivavideo.imkit.activity.CommonUtil;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.util.ArrayMap;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PictureSelectorActivity extends Activity {

    static public final int REQUEST_PREVIEW = 0;
    static public final int REQUEST_CAMERA = 1;
    static public final int REQUEST_CODE_ASK_PERMISSIONS = 100;

    private GridView mGridView;
    private ImageButton mBtnBack;
    private Button mBtnSend;
    private PicTypeBtn mPicType;
    private PreviewBtn mPreviewBtn;
    private View mCatalogView;
    private ListView mCatalogListView;

    private List<PicItem> mAllItemList;
    private Map<String, List<PicItem>> mItemMap;
    private List<String> mCatalogList;
    private String mCurrentCatalog = "";
    private Uri mTakePictureUri;
    private boolean mSendOrigin = false;
    private int perWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.rc_picsel_activity);

        mGridView = (GridView) findViewById(R.id.gridlist);
        mBtnBack = (ImageButton) findViewById(R.id.back);
        mBtnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mBtnSend = (Button) findViewById(R.id.send);
        mPicType = (PicTypeBtn) findViewById(R.id.pic_type);
        mPicType.init(this);
        mPicType.setEnabled(false);

        mPreviewBtn = (PreviewBtn) findViewById(R.id.preview);
        mPreviewBtn.init(this);
        mPreviewBtn.setEnabled(false);
        mCatalogView = findViewById(R.id.catalog_window);
        mCatalogListView = (ListView) findViewById(R.id.catalog_listview);

        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Class<?> contextClass = Context.class;
                Method checkSelfPermission = contextClass.getMethod("checkSelfPermission", new Class[]{String.class});
                int hasPermission = (int) checkSelfPermission.invoke(this, new Object[]{Manifest.permission.READ_EXTERNAL_STORAGE});

                Class<?> activityClass = Activity.class;
                Method shouldShowMethod = activityClass.getMethod("shouldShowRequestPermissionRationale", new Class[]{String.class});
                final Method requestPermission = activityClass.getMethod("requestPermissions", new Class[]{String[].class, int.class});

                if (hasPermission != PackageManager.PERMISSION_GRANTED) {
                    {
                        if ((boolean) shouldShowMethod.invoke(this, new Object[]{Manifest.permission.READ_EXTERNAL_STORAGE})) {
                            requestPermission.invoke(this, new Object[]{new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_ASK_PERMISSIONS});
                        } else {
                            new AlertDialog.Builder(this)
                                    .setMessage("您需要在设置里打开存储空间权限。")
                                    .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            try {
                                                requestPermission.invoke(this, new Object[]{new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_ASK_PERMISSIONS});
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .create().show();
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        initView();
    }

    private void initView() {
        updatePictureItems();

        mGridView.setAdapter(new GridViewAdapter());
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    return;
                }

                ArrayList<PicItem> list = new ArrayList<>();
                if (mCurrentCatalog.isEmpty()) {
                    list.addAll(mAllItemList);
                } else {
                    list.addAll(mItemMap.get(mCurrentCatalog));
                }
                Intent intent = new Intent(PictureSelectorActivity.this, PicturePreviewActivity.class);
                intent.putExtra("picList", list);
                intent.putExtra("index", position - 1);
                intent.putExtra("sendOrigin", mSendOrigin);
                startActivityForResult(intent, REQUEST_PREVIEW);
            }
        });

        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent data = new Intent();
                ArrayList<Uri> list = new ArrayList<>();
                for (String key : mItemMap.keySet()) {
                    for (PicItem item : mItemMap.get(key)) {
                        if (item.selected) {
                            list.add(Uri.parse("file://" + item.uri));
                        }
                    }
                }
                data.putExtra("sendOrigin", mSendOrigin);
                data.putExtra(Intent.EXTRA_RETURN_RESULT, list);
                setResult(RESULT_OK, data);
                finish();
            }
        });

        mPicType.setEnabled(true);
        mPicType.setTextColor(getResources().getColor(R.color.rc_picsel_toolbar_send_text_normal));
        mPicType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCatalogView.setVisibility(View.VISIBLE);
            }
        });

        mPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<PicItem> list = new ArrayList<>();
                for (String key : mItemMap.keySet()) {
                    for (PicItem item : mItemMap.get(key)) {
                        if (item.selected) {
                            list.add(item);
                        }
                    }
                }
                Intent intent = new Intent(PictureSelectorActivity.this, PicturePreviewActivity.class);
                intent.putExtra("sendOrigin", mSendOrigin);
                intent.putExtra("picList", list);
                startActivityForResult(intent, REQUEST_PREVIEW);
            }
        });

        mCatalogView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && mCatalogView.getVisibility() == View.VISIBLE) {
                    mCatalogView.setVisibility(View.GONE);
                }
                return true;
            }
        });

        mCatalogListView.setAdapter(new CatalogAdapter());
        mCatalogListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String catalog;
                if (position == 0) {
                    catalog = "";
                } else {
                    catalog = mCatalogList.get(position - 1);
                }
                if (catalog.equals(mCurrentCatalog)) {
                    mCatalogView.setVisibility(View.GONE);
                    return;
                }

                mCurrentCatalog = catalog;
                TextView textView = (TextView) view.findViewById(R.id.name);
                mPicType.setText(textView.getText().toString());
                mCatalogView.setVisibility(View.GONE);
                ((CatalogAdapter) mCatalogListView.getAdapter()).notifyDataSetChanged();
                ((GridViewAdapter) mGridView.getAdapter()).notifyDataSetChanged();
            }
        });

        AlbumBitmapCacheHelper.init(this);
        perWidth = (((WindowManager) (this.getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getWidth() - CommonUtil.dip2px(this, 4)) / 3;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            return;
        } else if (resultCode == PicturePreviewActivity.RESULT_SEND) {
            setResult(RESULT_OK, data);
            finish();
            return;
        }

        switch (requestCode) {
            case REQUEST_PREVIEW: {
                mSendOrigin = data.getBooleanExtra("sendOrigin", false);
                ArrayList<PicItem> list = (ArrayList<PicItem>) data.getSerializableExtra("picList");
                for (PicItem it : list) {
                    PicItem item = findByUri(it.uri);
                    if (item != null) {
                        item.selected = it.selected;
                    }
                }
                ((GridViewAdapter) mGridView.getAdapter()).notifyDataSetChanged();
                ((CatalogAdapter) mCatalogListView.getAdapter()).notifyDataSetChanged();
                updateToolbar();
            }
            break;
            case REQUEST_CAMERA: {
                ArrayList<PicItem> list = new ArrayList<>();
                PicItem item = new PicItem();
                item.uri = mTakePictureUri.getPath();
                list.add(item);

                Intent intent = new Intent(PictureSelectorActivity.this, PicturePreviewActivity.class);
                intent.putExtra("picList", list);
                startActivityForResult(intent, REQUEST_PREVIEW);

                MediaScannerConnection.scanFile(this, new String[]{mTakePictureUri.getPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        updatePictureItems();
                    }
                });
            }
            break;
            default:
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mCatalogView != null && mCatalogView.getVisibility() == View.VISIBLE) {
                mCatalogView.setVisibility(View.GONE);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    protected void requestCamera() {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!path.exists())
            path.mkdirs();
        String name = System.currentTimeMillis() + ".jpg";
        File file = new File(path, name);
        mTakePictureUri = Uri.fromFile(file);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mTakePictureUri);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    private void updatePictureItems() {
        String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED};
        String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, orderBy);

        mAllItemList = new ArrayList<>();
        mCatalogList = new ArrayList<>();
        mItemMap = new ArrayMap<>();
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                PicItem item = new PicItem();
                item.uri = cursor.getString(0);
                mAllItemList.add(item);

                int last = item.uri.lastIndexOf("/");
                String catalog;
                if (last == 0) {
                    catalog = "/";
                } else {
                    int secondLast = item.uri.lastIndexOf("/", last - 1);
                    catalog = item.uri.substring(secondLast + 1, last);
                }

                // Add item to mItemList.
                if (mItemMap.containsKey(catalog)) {
                    mItemMap.get(catalog).add(item);
                } else {
                    ArrayList<PicItem> itemList = new ArrayList<>();
                    itemList.add(item);
                    mItemMap.put(catalog, itemList);
                    mCatalogList.add(catalog);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    private int getTotalSelectedNum() {
        int sum = 0;
        for (String key : mItemMap.keySet()) {
            for (PicItem item : mItemMap.get(key)) {
                if (item.selected) {
                    sum++;
                }
            }
        }
        return sum;
    }

    private void updateToolbar() {
        int sum = getTotalSelectedNum();
        if (sum == 0) {
            mBtnSend.setEnabled(false);
            mBtnSend.setTextColor(getResources().getColor(R.color.rc_picsel_toolbar_send_text_disable));
            mBtnSend.setText(R.string.rc_picsel_toolbar_send);

            mPreviewBtn.setEnabled(false);
            mPreviewBtn.setText(R.string.rc_picsel_toolbar_preview);
        } else if (sum <= 9) {
            mBtnSend.setEnabled(true);
            mBtnSend.setTextColor(getResources().getColor(R.color.rc_picsel_toolbar_send_text_normal));
            mBtnSend.setText(String.format(getResources().getString(R.string.rc_picsel_toolbar_send_num), sum));

            mPreviewBtn.setEnabled(true);
            mPreviewBtn.setText(String.format(getResources().getString(R.string.rc_picsel_toolbar_preview_num), sum));
        }
    }

    private PicItem getItemAt(int index) {
        int sum = 0;
        for (String key : mItemMap.keySet()) {
            for (PicItem item : mItemMap.get(key)) {
                if (sum == index) {
                    return item;
                }
                sum++;
            }
        }
        return null;
    }

    private PicItem getItemAt(String catalog, int index) {
        if (!mItemMap.containsKey(catalog)) {
            return null;
        }
        int sum = 0;
        for (PicItem item : mItemMap.get(catalog)) {
            if (sum == index) {
                return item;
            }
            sum++;
        }
        return null;
    }

    private PicItem findByUri(String uri) {
        for (String key : mItemMap.keySet()) {
            for (PicItem item : mItemMap.get(key)) {
                if (item.uri.equals(uri)) {
                    return item;
                }
            }
        }
        return null;
    }

    private class GridViewAdapter extends BaseAdapter {

        private LayoutInflater mInflater;

        public GridViewAdapter() {
            mInflater = getLayoutInflater();
        }

        @Override
        public int getCount() {
            int sum = 1;
            if (mCurrentCatalog.isEmpty()) {
                for (String key : mItemMap.keySet()) {
                    sum += mItemMap.get(key).size();
                }
            } else {
                sum += mItemMap.get(mCurrentCatalog).size();
            }
            return sum;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (position == 0) {
                View view = mInflater.inflate(R.layout.rc_picsel_grid_camera, parent, false);
                final ImageButton mask = (ImageButton) view.findViewById(R.id.camera_mask);
                mask.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Build.VERSION.SDK_INT >= 23) {
                            try {
                                Class<?> contextClass = Context.class;
                                Method checkSelfPermission = contextClass.getMethod("checkSelfPermission", new Class[]{String.class});
                                int hasPermission = (int) checkSelfPermission.invoke(this, new Object[]{Manifest.permission.CAMERA});

                                Class<?> activityClass = Activity.class;
                                Method shouldShowMethod = activityClass.getMethod("shouldShowRequestPermissionRationale", new Class[]{String.class});
                                final Method requestPermission = activityClass.getMethod("requestPermissions", new Class[]{String[].class, int.class});

                                if (hasPermission != PackageManager.PERMISSION_GRANTED) {
                                    {
                                        if ((boolean) shouldShowMethod.invoke(this, new Object[]{Manifest.permission.CAMERA})) {
                                            requestPermission.invoke(this, new Object[]{new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_ASK_PERMISSIONS});
                                        } else {
                                            new AlertDialog.Builder(PictureSelectorActivity.this)
                                                    .setMessage("您需要在设置里打开相机权限。")
                                                    .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            try {
                                                                requestPermission.invoke(this, new Object[]{new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_ASK_PERMISSIONS});
                                                            } catch (Exception e) {
                                                            }
                                                        }
                                                    })
                                                    .setNegativeButton("取消", null)
                                                    .create().show();
                                        }
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        requestCamera();
                    }
                });
                return view;
            }

            final PicItem item;
            if (mCurrentCatalog.isEmpty()) {
                item = mAllItemList.get(position - 1);
            } else {
                item = getItemAt(mCurrentCatalog, position - 1);
            }

            View view = convertView;
            final ViewHolder holder;
            if (view == null || view.getTag() == null) {
                view = mInflater.inflate(R.layout.rc_picsel_grid_item, parent, false);
                holder = new ViewHolder();
                holder.image = (ImageView) view.findViewById(R.id.image);
                holder.mask = view.findViewById(R.id.mask);
                holder.checkBox = (SelectBox) view.findViewById(R.id.checkbox);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            if (holder.image.getTag() != null) {
                String path = (String) holder.image.getTag();
                AlbumBitmapCacheHelper.getInstance().removePathFromShowlist(path);
            }

            String path = item.uri;
            AlbumBitmapCacheHelper.getInstance().addPathToShowlist(path);
            holder.image.setTag(path);
            Bitmap bitmap = AlbumBitmapCacheHelper.getInstance().getBitmap(path, perWidth, perWidth, new AlbumBitmapCacheHelper.ILoadImageCallback() {
                @Override
                public void onLoadImageCallBack(Bitmap bitmap, String path1, Object... objects) {
                    if (bitmap == null) {
                        return;
                    }
                    BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                    View v = mGridView.findViewWithTag(path1);
                    if (v != null)
                        v.setBackgroundDrawable(bd);
                }
            }, position);
            if (bitmap != null) {
                BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                holder.image.setBackgroundDrawable(bd);
            } else {
                holder.image.setBackgroundResource(R.drawable.rc_grid_image_default);
            }

            holder.checkBox.setChecked(item.selected);
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!holder.checkBox.getChecked() && getTotalSelectedNum() == 9) {
                        Toast.makeText(getApplicationContext(), R.string.rc_picsel_selected_max, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    holder.checkBox.setChecked(!holder.checkBox.getChecked());
                    item.selected = holder.checkBox.getChecked();
                    if (item.selected) {
                        holder.mask.setBackgroundColor(getResources().getColor(R.color.rc_picsel_grid_mask_pressed));
                    } else {
                        holder.mask.setBackgroundDrawable(getResources().getDrawable(R.drawable.rc_sp_grid_mask));
                    }
                    updateToolbar();
                }
            });
            if (item.selected) {
                holder.mask.setBackgroundColor(getResources().getColor(R.color.rc_picsel_grid_mask_pressed));
            } else {
                holder.mask.setBackgroundDrawable(getResources().getDrawable(R.drawable.rc_sp_grid_mask));
            }

            return view;
        }

        private class ViewHolder {
            ImageView image;
            View mask;
            SelectBox checkBox;
        }
    }

    private class CatalogAdapter extends BaseAdapter {

        private LayoutInflater mInflater;

        public CatalogAdapter() {
            mInflater = getLayoutInflater();
        }

        @Override
        public int getCount() {
            return mItemMap.size() + 1;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            View view = convertView;
            if (view == null) {
                view = mInflater.inflate(R.layout.rc_picsel_catalog_listview, parent, false);
                holder = new ViewHolder();
                holder.image = (ImageView) view.findViewById(R.id.image);
                holder.name = (TextView) view.findViewById(R.id.name);
                holder.number = (TextView) view.findViewById(R.id.number);
                holder.selected = (ImageView) view.findViewById(R.id.selected);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            if (holder.image.getTag() != null) {
                String path = (String) holder.image.getTag();
                AlbumBitmapCacheHelper.getInstance().removePathFromShowlist(path);
            }

            String path;
            String name;
            int num = 0;
            boolean showSelected = false;
            if (position == 0) {
                if (mItemMap.size() == 0) {
                    holder.image.setImageResource(R.drawable.rc_picsel_empty_pic);
                } else {
                    path = mItemMap.get(mCatalogList.get(0)).get(0).uri;
                    AlbumBitmapCacheHelper.getInstance().addPathToShowlist(path);
                    holder.image.setTag(path);
                    Bitmap bitmap = AlbumBitmapCacheHelper.getInstance().getBitmap(path, perWidth, perWidth, new AlbumBitmapCacheHelper.ILoadImageCallback() {
                        @Override
                        public void onLoadImageCallBack(Bitmap bitmap, String path1, Object... objects) {
                            if (bitmap == null) {
                                return;
                            }
                            BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                            View v = mGridView.findViewWithTag(path1);
                            if (v != null)
                                v.setBackgroundDrawable(bd);
                        }
                    }, position);
                    if (bitmap != null) {
                        BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                        holder.image.setBackgroundDrawable(bd);
                    } else {
                        holder.image.setBackgroundResource(R.drawable.rc_grid_image_default);
                    }
                }
                name = getResources().getString(R.string.rc_picsel_catalog_allpic);
                holder.number.setVisibility(View.GONE);
                showSelected = mCurrentCatalog.isEmpty();
            } else {
                path = mItemMap.get(mCatalogList.get(position - 1)).get(0).uri;
                name = mCatalogList.get(position - 1);
                num = mItemMap.get(mCatalogList.get(position - 1)).size();
                holder.number.setVisibility(View.VISIBLE);
                showSelected = name.equals(mCurrentCatalog);

                AlbumBitmapCacheHelper.getInstance().addPathToShowlist(path);
                holder.image.setTag(path);
                Bitmap bitmap = AlbumBitmapCacheHelper.getInstance().getBitmap(path, perWidth, perWidth, new AlbumBitmapCacheHelper.ILoadImageCallback() {
                    @Override
                    public void onLoadImageCallBack(Bitmap bitmap, String path1, Object... objects) {
                        if (bitmap == null) {
                            return;
                        }
                        BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                        View v = mGridView.findViewWithTag(path1);
                        if (v != null)
                            v.setBackgroundDrawable(bd);
                    }
                }, position);
                if (bitmap != null) {
                    BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                    holder.image.setBackgroundDrawable(bd);
                } else {
                    holder.image.setBackgroundResource(R.drawable.rc_grid_image_default);
                }
            }
            holder.name.setText(name);
            holder.number.setText(String.format(getResources().getString(R.string.rc_picsel_catalog_number), num));
            holder.selected.setVisibility(showSelected ? View.VISIBLE : View.INVISIBLE);
            return view;
        }

        private class ViewHolder {
            ImageView image;
            TextView name;
            TextView number;
            ImageView selected;
        }
    }

    static public class PicItem implements Serializable {
        String uri;
        boolean selected;
    }

    static public class PicTypeBtn extends LinearLayout {

        TextView mText;

        public PicTypeBtn(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public void init(Activity root) {
            mText = (TextView) root.findViewById(R.id.type_text);
        }

        public void setText(String text) {
            mText.setText(text);
        }

        public void setTextColor(int color) {
            mText.setTextColor(color);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isEnabled()) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mText.setVisibility(View.INVISIBLE);
                        break;
                    case MotionEvent.ACTION_UP:
                        mText.setVisibility(View.VISIBLE);
                        break;
                    default:
                }
            }
            return super.onTouchEvent(event);
        }
    }

    static public class PreviewBtn extends LinearLayout {

        private TextView mText;

        public PreviewBtn(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public void init(Activity root) {
            mText = (TextView) root.findViewById(R.id.preview_text);
        }

        public void setText(int id) {
            mText.setText(id);
        }

        public void setText(String text) {
            mText.setText(text);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            int color = enabled ? R.color.rc_picsel_toolbar_send_text_normal
                    : R.color.rc_picsel_toolbar_send_text_disable;
            mText.setTextColor(getResources().getColor(color));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isEnabled()) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mText.setVisibility(View.INVISIBLE);
                        break;
                    case MotionEvent.ACTION_UP:
                        mText.setVisibility(View.VISIBLE);
                        break;
                    default:
                }
            }
            return super.onTouchEvent(event);
        }
    }


//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        switch (requestCode) {
//            case REQUEST_CODE_ASK_PERMISSIONS:
//                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    // Permission Granted
//                    if (permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
//                        initView();
//                    } else if (permissions[0].equals(Manifest.permission.CAMERA)) {
//                        requestCamera();
//                    }
//                }
//                break;
//            default:
//                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        }
//    }

    static public class SelectBox extends ImageView {

        private boolean mIsChecked;

        public SelectBox(Context context, AttributeSet attrs) {
            super(context, attrs);
            setImageResource(R.drawable.select_check_nor);
        }

        public void setChecked(boolean check) {
            mIsChecked = check;
            setImageResource(mIsChecked ? R.drawable.select_check_sel : R.drawable.select_check_nor);
        }

        public boolean getChecked() {
            return mIsChecked;
        }
    }

    @Override
    protected void onDestroy() {
        AlbumBitmapCacheHelper.getInstance().uninit();
        super.onDestroy();
    }
}
