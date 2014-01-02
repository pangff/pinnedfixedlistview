/*
 * Copyright (C) 2013 Sergej Shafarenka, halfbit.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pangff.pinnedsectionfixedlistview;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;

/**
 * ListView capable to pin views at its top while the rest is still scrolled.
 */
public class PinnedSectionListView extends ListView {

    //-- inner classes

	/** List adapter to be implemented for being used with PinnedSectionListView adapter. */
	public static interface PinnedSectionListAdapter extends ListAdapter {
		/** This method shall return 'true' if views of given type has to be pinned. */
		boolean isItemViewTypePinned(int viewType);
	}

	/** Wrapper class for pinned section view and its position in the list. */
	static class PinnedViewShadow {
		public View view;
		public int position;
	}

	//-- class fields

	/** Default change observer. */
	private final DataSetObserver mDataSetObserver = new DataSetObserver() {
	    @Override public void onChanged() {
	        destroyPinnedShadow();
	    };
	    @Override public void onInvalidated() {
	        destroyPinnedShadow();
	    }
    };

    /** Delegating listener, can be null. */
    OnScrollListener mDelegateOnScrollListener;

    /** Shadow for being recycled, can be null. */
    //PinnedViewShadow mRecycleShadow;

    /** shadow instance with a pinned view, can be null. */
    //PinnedViewShadow mPinnedShadow;
    Map<Integer,PinnedViewShadow> mPinnedShadowMap = Collections.synchronizedMap(new LinkedHashMap<Integer,PinnedViewShadow>());

    /** Pinned view Y-translation. We use it to stick pinned view to the next section. */

	/** Scroll listener which does the magic */
	private final OnScrollListener mOnScrollListener = new OnScrollListener() {

		@Override public void onScrollStateChanged(AbsListView view, int scrollState) {
			if (mDelegateOnScrollListener != null) { // delegate
				mDelegateOnScrollListener.onScrollStateChanged(view, scrollState);
			}
		}

		@Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		   
			if (mDelegateOnScrollListener != null) { // delegate
				mDelegateOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
			}

			// get expected adapter or fail
			PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) view.getAdapter();
			if (adapter == null || visibleItemCount == 0) return; // nothing to do

			int visibleSectionPosition = findFirstVisibleSectionPosition(firstVisibleItem, visibleItemCount);
			if (visibleSectionPosition == -1) { // there is no visible sections

				// try to find invisible view
				int currentSectionPosition = findCurrentSectionPosition(firstVisibleItem);
				if (currentSectionPosition == -1) return; // exit here, we have no sections
				// else, we have a section to pin
				createPinnedShadow(currentSectionPosition);
				return; // exit, as we have created a pinned candidate already
			}
			 int topBorder = getListPaddingTop()+getInitTop();
			if(visibleSectionPosition - firstVisibleItem < visibleItemCount){
	            int visibleSectionTop = view.getChildAt(visibleSectionPosition - firstVisibleItem).getTop();
	            if (!mPinnedShadowMap.containsKey(visibleSectionPosition)) {
	                if (visibleSectionTop < topBorder) {
	                    createPinnedShadow(visibleSectionPosition);
	                }
	            } else {
	                if (visibleSectionPosition == getLastPinnedViewShadow().position) {
	                    if (visibleSectionTop > topBorder - getLastPinnedViewShadow().view.getHeight()) {
	                        destroyPinnedShadow();
	                        visibleSectionPosition = findCurrentSectionPosition(visibleSectionPosition - 1);
	                        if (visibleSectionPosition > -1) {
	                            createPinnedShadow(visibleSectionPosition);
	                        }
	                    }

	                } else {

	                    int pinnedSectionBottom = topBorder + getLastPinnedViewShadow().view.getHeight();
	                    if (visibleSectionTop < pinnedSectionBottom) {
	                        if (visibleSectionTop < topBorder) {
	                            createPinnedShadow(visibleSectionPosition);
	                        }
	                    }
	                }
	            }
			}

		}
	};

	//-- class methods
	
	private PinnedViewShadow getLastPinnedViewShadow(){
	  PinnedViewShadow pinnedViewShadow = null;
	  Iterator<Integer> it = mPinnedShadowMap.keySet().iterator();
	  int position = -1;
	  while(it.hasNext()){
          int tempPosition = it.next();
          if(position<tempPosition){
            position = tempPosition;
          }
	  }
	  if(mPinnedShadowMap.containsKey(position)){
	    pinnedViewShadow = mPinnedShadowMap.get(position);
	  }
	  return pinnedViewShadow;
	}

    public PinnedSectionListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PinnedSectionListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        setOnScrollListener(mOnScrollListener);
    }

	/** Create shadow wrapper with a pinned view for a view at given position */
	private synchronized void createPinnedShadow(int position) {
	 

		// try to recycle shadow
		PinnedViewShadow pinnedShadow = mPinnedShadowMap.get(position);// mRecycleShadow;
		//mRecycleShadow = null;

		// create new shadow, if needed
		if (pinnedShadow == null){
		    pinnedShadow = new PinnedViewShadow();
	        // request new view using recycled view, if such
	        View pinnedView = getAdapter().getView(position, pinnedShadow.view, PinnedSectionListView.this);

	        // read layout parameters
	        LayoutParams layoutParams = (LayoutParams) pinnedView.getLayoutParams();

	        int heightMode, heightSize;
	        if (layoutParams == null) { // take care for missing layout parameters
	            heightMode = MeasureSpec.AT_MOST;
	            heightSize = getHeight();
	        } else {
	            heightMode = MeasureSpec.getMode(layoutParams.height);
	            heightSize = MeasureSpec.getSize(layoutParams.height);
	        }

	        if (heightMode == MeasureSpec.UNSPECIFIED) heightMode = MeasureSpec.EXACTLY;

	        int maxHeight = getHeight() - getListPaddingTop() - getListPaddingBottom();
	        if (heightSize > maxHeight) heightSize = maxHeight;

	        // measure & layout
	        int ws = MeasureSpec.makeMeasureSpec(getWidth() - getListPaddingLeft() - getListPaddingRight(), MeasureSpec.EXACTLY);
	        int hs = MeasureSpec.makeMeasureSpec(heightSize, heightMode);
	        pinnedView.measure(ws, hs);
	        
	        int initTop = getInitTop();
	        pinnedView.layout(0, initTop, pinnedView.getMeasuredWidth(), initTop+pinnedView.getMeasuredHeight());

	        // initialize pinned shadow
	        pinnedShadow.position = position;
	        pinnedShadow.view = pinnedView;
	        mPinnedShadowMap.put(position, pinnedShadow);
		}
	}
	
	private int getInitTop(){
	  Iterator<Integer> it = mPinnedShadowMap.keySet().iterator();
      int initTop =  0;
      while(it.hasNext()){
        int viewId = it.next();
        initTop+=((PinnedViewShadow)mPinnedShadowMap.get(viewId)).view.getMeasuredHeight();
      }
      return initTop;
	}
	

	/** Destroy shadow wrapper for currently pinned view */
	private void destroyPinnedShadow() {
	    if(getLastPinnedViewShadow()!=null){
	      mPinnedShadowMap.remove(getLastPinnedViewShadow().position);
	    }
	}

	private int findFirstVisibleSectionPosition(int firstVisibleItem, int visibleItemCount) {
	    if(mPinnedShadowMap.size()>0){
	      firstVisibleItem = firstVisibleItem + mPinnedShadowMap.size();
	    }
		PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();
		for (int childIndex = 0; childIndex < visibleItemCount; childIndex++) {
			int position = firstVisibleItem + childIndex;
			if(position<getAdapter().getCount()){
			  int viewType = adapter.getItemViewType(position);
	            if (adapter.isItemViewTypePinned(viewType)) {
	              return position;
	            }
			}
			
		}
		return -1;
	}

	private int findCurrentSectionPosition(int fromPosition) {
		PinnedSectionListAdapter adapter = (PinnedSectionListAdapter) getAdapter();

		if (adapter instanceof SectionIndexer) {
			// try fast way by asking section indexer
			SectionIndexer indexer = (SectionIndexer) adapter;
			int sectionPosition = indexer.getSectionForPosition(fromPosition);
			int itemPosition = indexer.getPositionForSection(sectionPosition);
			int typeView = adapter.getItemViewType(itemPosition);
			if (adapter.isItemViewTypePinned(typeView)) {
				return itemPosition;
			} // else, no luck
		}

		// try slow way by looking through to the next section item above
		for (int position=fromPosition; position>=0; position--) {
			int viewType = adapter.getItemViewType(position);
			if (adapter.isItemViewTypePinned(viewType)) return position;
		}
		return -1; // no candidate found
	}

	@Override
	public void setOnScrollListener(OnScrollListener listener) {
		if (listener == mOnScrollListener) {
			super.setOnScrollListener(listener);
		} else {
			mDelegateOnScrollListener = listener;
		}
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);

		// restore pinned view after configuration change
		post(new Runnable() {
			@Override public void run() {
				ListAdapter adapter = getAdapter();
				if (adapter == null) return; // nothing to restore as adapter is still null

				// detect pinned position
				int firstVisiblePosition = getFirstVisiblePosition();
				int position = findCurrentSectionPosition(firstVisiblePosition);
				if (position == -1) return; // no views to pin, exit

				createPinnedShadow(firstVisiblePosition);
			}
		});
	}

	@Override
	public void setAdapter(ListAdapter adapter) {

	    // assert adapter in debug mode
		if (BuildConfig.DEBUG && adapter != null) {
			if (!(adapter instanceof PinnedSectionListAdapter))
				throw new IllegalArgumentException("Does your adapter implement PinnedSectionListAdapter?");
			if (adapter.getViewTypeCount() < 2)
				throw new IllegalArgumentException("Does your adapter handle at least two types of views - items and sections?");
		}

		// unregister observer at old adapter and register on new one
		ListAdapter currentAdapter = getAdapter();
		if (currentAdapter != null) currentAdapter.unregisterDataSetObserver(mDataSetObserver);
		if (adapter != null) adapter.registerDataSetObserver(mDataSetObserver);

		// destroy pinned shadow, if new adapter is not same as old one
		if (currentAdapter != adapter) destroyPinnedShadow();

		super.setAdapter(adapter);
	}

	@Override
	protected synchronized void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		if (mPinnedShadowMap.size() != 0) {

			// prepare variables
			int pLeft = getListPaddingLeft();
			int pTop = getListPaddingTop();
			Iterator<Integer> it = mPinnedShadowMap.keySet().iterator();
			
		    while(it.hasNext()){
		        canvas.save();
		        int position = it.next();
		        View view = mPinnedShadowMap.get(position).view;//mPinnedShadow.view;
	           
	            canvas.clipRect(pLeft, view.getTop(), pLeft + view.getWidth(), view.getTop()+pTop + view.getHeight());
	            drawChild(canvas, view, getDrawingTime());
	            canvas.restore();
		    }
		    
		}
	}
}
