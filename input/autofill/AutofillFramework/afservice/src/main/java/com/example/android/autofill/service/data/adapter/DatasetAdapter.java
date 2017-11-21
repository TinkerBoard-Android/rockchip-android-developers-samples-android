/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.autofill.service.data.adapter;

import android.app.assist.AssistStructure;
import android.content.IntentSender;
import android.service.autofill.Dataset;
import android.support.annotation.NonNull;
import android.util.MutableBoolean;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.example.android.autofill.service.AutofillHints;
import com.example.android.autofill.service.StructureParser;
import com.example.android.autofill.service.model.DatasetWithFilledAutofillFields;
import com.example.android.autofill.service.model.FilledAutofillField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.example.android.autofill.service.util.Util.logv;
import static com.example.android.autofill.service.util.Util.logw;
import static java.util.stream.Collectors.toMap;

public class DatasetAdapter {
    private final StructureParser mStructureParser;

    public DatasetAdapter(StructureParser structureParser) {
        mStructureParser = structureParser;
    }

    /**
     * Helper method for getting the index of a CharSequence object in an array.
     */
    private static int indexOf(@NonNull CharSequence[] array, CharSequence charSequence) {
        int index = -1;
        if (charSequence == null) {
            return index;
        }
        for (int i = 0; i < array.length; i++) {
            if (charSequence.equals(array[i])) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * Wraps autofill data in a {@link Dataset} object which can then be sent back to the client.
     */
    public Dataset buildDataset(DatasetWithFilledAutofillFields datasetWithFilledAutofillFields,
            RemoteViews remoteViews) {
        return buildDataset(datasetWithFilledAutofillFields, remoteViews, null);
    }

    /**
     * Wraps autofill data in a {@link Dataset} object with an IntentSender, which can then be
     * sent back to the client.
     */
    public Dataset buildDataset(DatasetWithFilledAutofillFields datasetWithFilledAutofillFields,
            RemoteViews remoteViews, IntentSender intentSender) {
        Dataset.Builder datasetBuilder = new Dataset.Builder(remoteViews);
        if (intentSender != null) {
            datasetBuilder.setAuthentication(intentSender);
        }
        boolean setAtLeastOneValue = bindDataset(datasetWithFilledAutofillFields, datasetBuilder);
        if (!setAtLeastOneValue) {
            return null;
        }
        return datasetBuilder.build();
    }

    /**
     * Build an autofill {@link Dataset} using saved data and the client's AssistStructure.
     */
    private boolean bindDataset(DatasetWithFilledAutofillFields datasetWithFilledAutofillFields,
            Dataset.Builder datasetBuilder) {
        MutableBoolean setValueAtLeastOnce = new MutableBoolean(false);
        Map<String, FilledAutofillField> map = datasetWithFilledAutofillFields.filledAutofillFields
                .stream().collect(toMap(FilledAutofillField::getHint, Function.identity()));
        mStructureParser.parse((node) ->
                parseAutofillFields(node, map, datasetBuilder, setValueAtLeastOnce)
        );
        return setValueAtLeastOnce.value;
    }

    private void parseAutofillFields(AssistStructure.ViewNode viewNode,
            Map<String, FilledAutofillField> map, Dataset.Builder builder,
            MutableBoolean setValueAtLeastOnce) {
        String[] rawHints = viewNode.getAutofillHints();
        if (rawHints == null || rawHints.length == 0) {
            logv("No af hints at ViewNode - %s", viewNode.getIdEntry());
            return;
        }
        List<String> hints = AutofillHints.convertToStoredHintNames(Arrays.asList(rawHints));
        // For simplicity, even if the viewNode has multiple autofill hints, only look at the first
        // one.
        String autofillHint = hints.get(0);
        FilledAutofillField field = map.get(autofillHint);
        if (field == null) {
            return;
        }
        AutofillId autofillId = viewNode.getAutofillId();
        if (autofillId == null) {
            logw("Autofill ID null for %s", viewNode.toString());
            return;
        }
        int autofillType = viewNode.getAutofillType();
        switch (autofillType) {
            case View.AUTOFILL_TYPE_LIST:
                CharSequence[] options = viewNode.getAutofillOptions();
                int listValue = -1;
                if (options != null) {
                    listValue = indexOf(viewNode.getAutofillOptions(), field.getTextValue());
                }
                if (listValue != -1) {
                    builder.setValue(autofillId, AutofillValue.forList(listValue));
                    setValueAtLeastOnce.value = true;
                }
                break;
            case View.AUTOFILL_TYPE_DATE:
                Long dateValue = field.getDateValue();
                if (dateValue != null) {
                    builder.setValue(autofillId, AutofillValue.forDate(dateValue));
                    setValueAtLeastOnce.value = true;
                }
                break;
            case View.AUTOFILL_TYPE_TEXT:
                String textValue = field.getTextValue();
                if (textValue != null) {
                    builder.setValue(autofillId, AutofillValue.forText(textValue));
                    setValueAtLeastOnce.value = true;
                }
                break;
            case View.AUTOFILL_TYPE_TOGGLE:
                Boolean toggleValue = field.getToggleValue();
                if (toggleValue != null) {
                    builder.setValue(autofillId, AutofillValue.forToggle(toggleValue));
                    setValueAtLeastOnce.value = true;
                }
                break;
            case View.AUTOFILL_TYPE_NONE:
            default:
                logw("Invalid autofill type - %d", autofillType);
                break;
        }
    }
}
