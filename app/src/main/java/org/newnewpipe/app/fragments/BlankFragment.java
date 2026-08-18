package org.newnewpipe.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.newnewpipe.app.BaseFragment;
import org.newnewpipe.app.R;

public class BlankFragment extends BaseFragment {
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        setTitle("NewNewPipe");
        return inflater.inflate(R.layout.fragment_blank, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle("NewNewPipe");
    }
}
