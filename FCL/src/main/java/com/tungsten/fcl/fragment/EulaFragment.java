package com.tungsten.fcl.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tungsten.fcl.R;
import com.tungsten.fcl.activity.SplashActivity;
import com.tungsten.fclcore.util.io.IOUtils;
import com.tungsten.fcllibrary.component.FCLFragment;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLProgressBar;
import com.tungsten.fcllibrary.component.view.FCLTextView;
import com.umeng.commonsdk.UMConfigure;

import java.io.IOException;

public class EulaFragment extends FCLFragment implements View.OnClickListener {

    private FCLProgressBar progressBar;
    private FCLTextView eula;
    private FCLButton next;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_eula, container, false);

        progressBar = findViewById(view, R.id.progress);
        eula = findViewById(view, R.id.eula);
        next = findViewById(view, R.id.next);
        next.setOnClickListener(this);

        loadEula();

        return view;
    }

    private void loadEula() {
        new Thread(() -> {
            String str;
            try {
                str = IOUtils.readFullyAsString(requireActivity().getAssets().open("eula.txt"));
            } catch (IOException e) {
                e.printStackTrace();
                str = getString(R.string.splash_eula_error);
            }
            final String s = str;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    eula.setText(s);
                });
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        if (view == next) {
            if (getActivity() != null) {
                // 用户同意 EULA，进行友盟正式初始化
                // 注意：appkey 和 channel 必须与 Application 中 preInit 使用的保持一致
                // 如果已在 AndroidManifest.xml 中配置，可以传 null；否则需显式传入相同值
                UMConfigure.init(
                    requireActivity(),
                    null,          // 与 preInit 保持一致的 appkey，或 null（如果 Manifest 已配置）
                    null,          // 与 preInit 保持一致的 channel，或 null（如果 Manifest 已配置）
                    UMConfigure.DEVICE_TYPE_PHONE,
                    null
                );
                // 可选：开启调试日志（正式发布时建议关闭）
                // UMConfigure.setLogEnabled(true);

                SharedPreferences sharedPreferences = getActivity().getSharedPreferences("launcher", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("isFirstLaunch", false);
                editor.apply();
                ((SplashActivity) getActivity()).start();
            }
        }
    }
}
