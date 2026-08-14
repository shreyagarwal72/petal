package com.petal.browser.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomRedirectsHelper;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.view.AdapterCustomRedirect;
import com.petal.browser.view.NinjaToast;

public class CustomRedirectsDialog extends DialogFragment {
    AdapterCustomRedirect adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = View.inflate(getContext(), R.layout.custom_redirects_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.redirects_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());

        ArrayList<CustomRedirect> redirects = new ArrayList<>();
        try {
            redirects = CustomRedirectsHelper.getRedirects(sp);
        } catch (JSONException e) {
            Log.e("Redirects parsing", e.toString());
        }

        adapter = new AdapterCustomRedirect(redirects, requireContext());
        recyclerView.setAdapter(adapter);

        builder.setTitle(R.string.privacy_redirect_title);
        builder.setIcon(R.drawable.icon_redirect);
        builder.setNegativeButton(R.string.create_new, null);
        builder.setPositiveButton(R.string.app_cancel, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        HelperUnit.setupDialog(requireContext(), dialog);
        // when the button to create a new entry is clicked, don't close the dialog
        dialog.setOnShowListener(dI -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            HelperUnit.applyBouncyTouchFeedback(b);
            b.setOnClickListener(view -> showCreateNewDialog());
        });
        return dialog;
    }

    private void showCreateNewDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = View.inflate(getContext(), R.layout.create_new_redirect, null);
        TextInputEditText source = dialogView.findViewById(R.id.source);
        TextInputEditText target = dialogView.findViewById(R.id.target);
        builder.setTitle(R.string.privacy_redirect_title);
        builder.setIcon(R.drawable.icon_redirect);
        builder.setPositiveButton(R.string.app_cancel, null);
        builder.setNegativeButton(R.string.app_ok, ((dialogInterface, i) -> {
            String sourceText = Objects.requireNonNull(source.getText()).toString();
            String targetText = Objects.requireNonNull(target.getText()).toString();
            if (targetText.isEmpty() && sourceText.isEmpty()) {
                NinjaToast.show(requireContext(), R.string.toast_input_empty);
                return;
            }
            adapter.addRedirect(new CustomRedirect(sourceText, targetText));
            try {
                CustomRedirectsHelper.saveRedirects(adapter.getRedirects());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }));
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(requireContext(), dialog);
    }
}
