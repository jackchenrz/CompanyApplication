package com.publish.monitorsystem.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.ButterKnife;
import butterknife.InjectView;

import com.msystemlib.base.BaseActivity;
import com.pow.api.cls.RfidPower.PDATYPE;
import com.publish.monitorsystem.R;
import com.publish.monitorsystem.api.bean.UploadInventoryEqpt;
import com.publish.monitorsystem.api.bean.EqptBean.Eqpt;
import com.publish.monitorsystem.api.db.dao.EqptDao;
import com.publish.monitorsystem.api.db.dao.InventoryEqptDao;
import com.publish.monitorsystem.api.db.dao.UploadInventoryDao;
import com.publish.monitorsystem.api.db.dao.UploadInventoryEqptDao;
import com.publish.monitorsystem.api.readrfid.IRunneableReaderListener;
import com.publish.monitorsystem.api.readrfid.Runnable_Reader;
import com.publish.monitorsystem.application.SysApplication;
import com.uhf.api.cls.Reader.READER_ERR;
import com.uhf.api.cls.Reader.TAGINFO;

public class InventoryActivity extends BaseActivity {
	@InjectView(R.id.btn_start)
	Button btnStart;
	@InjectView(R.id.btn_stop)
	Button btnStop;
	@InjectView(R.id.tv_title)
	TextView tv_title;
	@InjectView(R.id.lv_inventoryeqpt)
	ListView listView;
	
	private InventoryEqptDao inventoryEqptDao;
	private UploadInventoryEqptDao uploadInventoryeqptDao;
	private EqptDao eqptDao;
	private UploadInventoryDao uploadInventoryDao;
	
	private List<Eqpt> noInventoryEqptlist;
	private List<Eqpt> yesInventoryEqptlist;
	private ArrayList<Eqpt> inventoryEqptlist;
	Map<String, TAGINFO> Devaddrs = new LinkedHashMap<String, TAGINFO>();// 有序
	private SoundPool soundPool;
	private SysApplication myapp;
	private Runnable_Reader runnable;
	private String planID;
	private String roomID;
	private Handler handler = new Handler();
	private CommonAdapter<Eqpt> adapter;

	@Override
	public int bindLayout() {
		return R.layout.activity_inventory;
	}

	@Override
	public void initView(View view) {
		ButterKnife.inject(this);
		tv_title.setText("设备盘点");
	}

	@Override
	public void doBusiness(Context mContext) {
		Intent intent = getIntent();
		planID = intent.getStringExtra("planID");
		roomID = intent.getStringExtra("roomID");
		
		inventoryEqptDao = InventoryEqptDao.getInstance(this);
		uploadInventoryDao = UploadInventoryDao.getInstance(this);
		eqptDao = EqptDao.getInstance(this);
		uploadInventoryeqptDao = UploadInventoryEqptDao.getInstance(this);
		frushAdapter();

		soundPool = new SoundPool(10, AudioManager.STREAM_SYSTEM, 5);
		soundPool.load(this, R.raw.beep, 1);
		Application app = getApplication();
		myapp = (SysApplication) app;
		runnable = new Runnable_Reader(myapp);
		myapp.Rparams = myapp.new ReaderParams();
		btnStop.setEnabled(false);
		btnStart.setOnClickListener(new OnClickListener() {

			@SuppressWarnings("unused")
			@Override
			public void onClick(View arg0) {
				try {

					if (myapp.ThreadMODE == 0){
						handler.postDelayed(runnable, 0);
						runnable.setOnReadListener(new IRunneableReaderListener() {
							
							@Override
							public void setView() {
								frushAdapter();
							}
							
							@Override
							public void setTagInventory_Raw(READER_ERR er) {
								handler.postDelayed(runnable, myapp.Rparams.sleep);
							}
							
							@Override
							public void setReadoncecnt(int i) {
								
							}
							
							@Override
							public void setReaderSound(String tag, TAGINFO tfs) {
								if (!myapp.Devaddrs.containsKey(planID + tag)
										&& !Devaddrs.containsKey(planID+ tag)) {
									soundPool.play(1, 0.2f, 0.8f, 0, 0, 1);
									SystemClock.sleep(100);
									soundPool.play(1, 0.8f, 0.2f, 0, 0, 1);
									Devaddrs.put(planID + tag, tfs);
									Eqpt eqptByEPC = eqptDao.getEqptByEPC(tag);
									inventoryEqptDao.updateInventoryEqpt(eqptByEPC.EquipmentID, planID);

									// 保存盘点设备信息
									Date date = new Date();// 创建一个时间对象，获取到当前的时间
									SimpleDateFormat sdf = new SimpleDateFormat(
											"yyyy/MM/dd HH:mm");// 设置时间显示格式
									String str = sdf.format(date);// 将当前时间格式化为需要的类型
									if(inventoryEqptDao.isInventoryEqptID(eqptByEPC.EquipmentID,planID)
											&&!uploadInventoryeqptDao.containsEqptID(eqptByEPC.EquipmentID)){
										UploadInventoryEqpt eqpt = new UploadInventoryEqpt();
										eqpt.InfoID = UUID.randomUUID().toString();
										eqpt.RoomID = roomID;
										eqpt.PlanID = planID;
										eqpt.InventoryID = uploadInventoryDao.getAllUploadInventory().get(0).InventoryID;
										eqpt.ParentPlanID = inventoryEqptDao.getParentPlanID(planID);
										eqpt.EquipmentID = eqptByEPC.EquipmentID;
										eqpt.InventoryTime = str;
										uploadInventoryeqptDao.addUploadInventoryEqpt(eqpt);
									}
								}
							}
							
							@Override
							public void setReaderError(READER_ERR er) {
								handler.removeCallbacks(runnable);

								btnStart.setText("读");
							}
							
							@Override
							public void setReadNum() {
								handler.postDelayed(runnable, myapp.Rparams.sleep);
							}
						});
					}
					ReadHandleUI();
				} catch (Exception ex) {
					Toast.makeText(InventoryActivity.this,
							"开始盘点失败：" + ex.getMessage(), Toast.LENGTH_SHORT)
							.show();
				}
			}
		});

		btnStop.setOnClickListener(new OnClickListener() {

			@SuppressWarnings("unused")
			@Override
			public void onClick(View arg0) {
				if (myapp.ThreadMODE == 0)
					handler.removeCallbacks(runnable);

				if (myapp.Rpower.GetType() == PDATYPE.SCAN_ALPS_ANDROID_CUIUS2) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				myapp.Devaddrs.putAll(Devaddrs);
				StopHandleUI();
			}

		});
	}

	private void frushAdapter() {
		noInventoryEqptlist = inventoryEqptDao.getInventoryEqptList(0 + "",
				planID);
		yesInventoryEqptlist = inventoryEqptDao.getInventoryEqptList(1 + "",
				planID);
		inventoryEqptlist = new ArrayList<Eqpt>();
		inventoryEqptlist.addAll(noInventoryEqptlist);
		inventoryEqptlist.addAll(yesInventoryEqptlist);
		adapter = new CommonAdapter<Eqpt>(inventoryEqptlist) {

			@Override
			public View getView(int arg0, View arg1, ViewGroup arg2) {
				TextView tv = new TextView(InventoryActivity.this);
				String string = inventoryEqptlist.get(arg0).EquipmentCode
						+ "\t\t" + inventoryEqptlist.get(arg0).EquipmentName;
				tv.setText(string);
				tv.setTextColor(Color.RED);
				if ((arg0 + 1) > inventoryEqptlist.size()
						- yesInventoryEqptlist.size()) {
					tv.setTextColor(Color.BLACK);
					arg1.setClickable(false);
				}
				return tv;
			}
		};
		listView.setAdapter(adapter);
		if (btnStart.isEnabled()) {
			listView.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1,
						int arg2, long arg3) {
					if ((arg2 + 1) < inventoryEqptlist.size()
							- yesInventoryEqptlist.size()
							|| (arg2 + 1) == inventoryEqptlist.size()
									- yesInventoryEqptlist.size()) {
						Eqpt eqpt = inventoryEqptlist.get(arg2);
						Intent intent = new Intent(InventoryActivity.this,
								InventoryhandActivity.class);
						intent.putExtra("eqpt", eqpt);
						intent.putExtra("planID", planID);
						intent.putExtra("roomID", roomID);
						startActivity(intent);
						overridePendingTransition(R.anim.base_slide_right_in,
								R.anim.base_slide_remain);
					}
				}
			});
		}
	}
	private void ReadHandleUI() {
		this.btnStart.setEnabled(false);
		this.btnStop.setEnabled(true);
	}

	private void StopHandleUI() {
		this.btnStart.setEnabled(true);
		this.btnStop.setEnabled(false);
	}

	@Override
	public void resume() {
		frushAdapter();
	}

	@Override
	public void destroy() {
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (btnStop.isEnabled()) {
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);

	}
}