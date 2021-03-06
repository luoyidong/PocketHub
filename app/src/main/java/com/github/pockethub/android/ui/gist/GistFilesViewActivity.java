/*
 * Copyright (c) 2015 PocketHub
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pockethub.android.ui.gist;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.User;
import com.github.pockethub.android.Intents.Builder;
import com.github.pockethub.android.R;
import com.github.pockethub.android.core.gist.FullGist;
import com.github.pockethub.android.core.gist.GistStore;
import com.github.pockethub.android.core.gist.RefreshGistTask;
import com.github.pockethub.android.ui.FragmentProvider;
import com.github.pockethub.android.ui.PagerActivity;
import com.github.pockethub.android.ui.ViewPager;
import com.github.pockethub.android.util.AvatarLoader;
import com.github.pockethub.android.util.HttpImageGetter;
import com.google.inject.Inject;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static com.github.pockethub.android.Intents.EXTRA_GIST_ID;
import static com.github.pockethub.android.Intents.EXTRA_POSITION;

/**
 * Activity to page through the content of all the files in a Gist
 */
public class GistFilesViewActivity extends PagerActivity {

    /**
     * Create intent to show files with an initial selected file
     *
     * @param gist
     * @param position
     * @return intent
     */
    public static Intent createIntent(Gist gist, int position) {
        return new Builder("gist.files.VIEW").gist(gist.id())
            .add(EXTRA_POSITION, position).toIntent();
    }

    private String gistId;

    private int initialPosition;

    private ViewPager pager;

    private ProgressBar loadingBar;

    private TabLayout tabs;

    private Gist gist;

    @Inject
    private GistStore store;

    @Inject
    private AvatarLoader avatars;

    @Inject
    private HttpImageGetter imageGetter;

    private GistFilesPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gistId = getStringExtra(EXTRA_GIST_ID);
        initialPosition = getIntExtra(EXTRA_POSITION);

        setContentView(R.layout.activity_pager_with_title);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        pager = (ViewPager) findViewById(R.id.vp_pages);
        loadingBar = (ProgressBar) findViewById(R.id.pb_loading);
        tabs = (TabLayout) findViewById(R.id.sliding_tabs_layout);

        if (initialPosition < 0) {
            initialPosition = 0;
        }

        getSupportActionBar().setTitle(getString(R.string.gist_title) + gistId);

        gist = store.getGist(gistId);
        if (gist != null) {
            configurePager();
        } else {
            loadingBar.setVisibility(View.VISIBLE);
            pager.setVisibility(View.GONE);
            tabs.setVisibility(View.GONE);
            Single.create(new RefreshGistTask(this, gistId, imageGetter))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this.bindToLifecycle())
                    .subscribe(gist -> {
                        this.gist = gist.getGist();
                        configurePager();
                    });
        }
    }

    private void configurePager() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        User author = gist.owner();
        if (author != null) {
            actionBar.setSubtitle(author.login());
            avatars.bind(actionBar, author);
        } else {
            actionBar.setSubtitle(R.string.anonymous);
        }

        loadingBar.setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        tabs.setVisibility(View.VISIBLE);

        adapter = new GistFilesPagerAdapter(this, gist);
        pager.setAdapter(adapter);
        tabs.setupWithViewPager(pager);

        if (initialPosition < adapter.getCount()) {
            pager.scheduleSetItem(initialPosition);
            onPageSelected(initialPosition);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (gist != null) {
                    Intent intent = GistsViewActivity.createIntent(gist);
                    intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP
                        | FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected FragmentProvider getProvider() {
        return adapter;
    }
}
