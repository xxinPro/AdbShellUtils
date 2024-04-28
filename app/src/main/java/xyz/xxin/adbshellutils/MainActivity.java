package xyz.xxin.adbshellutils;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private final static int PERMISSION_CODE = 10001;
    private boolean shizukuServiceState = false;
    private Button judge_permission;
    private Button request_permission;
    private Button connect_shizuku;
    private Button execute_command;
    private EditText input_command;
    private TextView execute_result;
    private IUserService iUserService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findView();
        addEvent();
        initShizuku();
    }


    private void initShizuku() {
        // 添加权限申请监听
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);

        // Shiziku服务启动时调用该监听
        Shizuku.addBinderReceivedListenerSticky(onBinderReceivedListener);

        // Shiziku服务终止时调用该监听
        Shizuku.addBinderDeadListener(onBinderDeadListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除权限申请监听
        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener);

        Shizuku.removeBinderReceivedListener(onBinderReceivedListener);

        Shizuku.removeBinderDeadListener(onBinderDeadListener);

        Shizuku.unbindUserService(userServiceArgs, serviceConnection, true);
    }

    private final Shizuku.OnBinderReceivedListener onBinderReceivedListener = () -> {
        shizukuServiceState = true;
        Toast.makeText(MainActivity.this, "Shizuku服务已启动", Toast.LENGTH_SHORT).show();
    };

    private final Shizuku.OnBinderDeadListener onBinderDeadListener = () -> {
        shizukuServiceState = false;
        iUserService = null;
        Toast.makeText(MainActivity.this, "Shizuku服务被终止", Toast.LENGTH_SHORT).show();
    };

    private void addEvent() {
        // 判断权限
        judge_permission.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkPermission()) {
                Toast.makeText(this, "已拥有权限", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未拥有权限", Toast.LENGTH_SHORT).show();
            }
        });

        // 动态申请权限
        request_permission.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            requestShizukuPermission();
        });

        // 连接Shizuku服务
        connect_shizuku.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!checkPermission()) {
                Toast.makeText(this, "没有Shizuku权限", Toast.LENGTH_SHORT).show();
                return;
            }

            if (iUserService != null) {
                Toast.makeText(MainActivity.this, "已连接Shizuku服务", Toast.LENGTH_SHORT).show();
                return;
            }

            // 绑定shizuku服务
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        });

        // 执行命令
        execute_command.setOnClickListener(view -> {
            if (iUserService == null) {
                Toast.makeText(this, "请先连接Shizuku服务", Toast.LENGTH_SHORT).show();
                return;
            }

            String shellCommand = input_command.getText().toString();

            if ("".equals(shellCommand.trim().replaceAll(" ", ""))) {
                Toast.makeText(this, "命令不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 将执行结果显示
            try {
                String result = iUserService.exec(shellCommand);

                if ("".equals(result.replaceAll(" ", ""))) {
                    result = "null";
                }

                execute_result.setText(result);
            } catch (RemoteException e) {
                execute_result.setText(e.toString());
                e.printStackTrace();
            }
        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Toast.makeText(MainActivity.this, "Shizuku服务连接成功", Toast.LENGTH_SHORT).show();

            if (iBinder != null && iBinder.pingBinder()) {
                iUserService = IUserService.Stub.asInterface(iBinder);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Toast.makeText(MainActivity.this, "Shizuku服务连接断开", Toast.LENGTH_SHORT).show();
            iUserService = null;
        }
    };

    private final Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("adb_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);


    /**
     * 动态申请Shizuku adb shell权限
     */
    private void requestShizukuPermission() {
        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "当前shizuku版本不支持动态申请权限", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkPermission()) {
            Toast.makeText(this, "已拥有Shizuku权限", Toast.LENGTH_SHORT).show();
            return;
        }

        // 动态申请权限
        Shizuku.requestPermission(MainActivity.PERMISSION_CODE);
    }

    private final Shizuku.OnRequestPermissionResultListener onRequestPermissionResultListener = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                Toast.makeText(MainActivity.this, "Shizuku授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Shizuku授权失败", Toast.LENGTH_SHORT).show();
            }
        }
    };

    /**
     * 判断是否拥有shizuku adb shell权限
     */
    private boolean checkPermission() {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void findView() {
        judge_permission = findViewById(R.id.judge_permission);
        request_permission = findViewById(R.id.request_permission);
        connect_shizuku = findViewById(R.id.connect_shizuku);
        execute_command = findViewById(R.id.execute_command);
        input_command = findViewById(R.id.input_command);
        execute_result = findViewById(R.id.execute_result);
    }
}