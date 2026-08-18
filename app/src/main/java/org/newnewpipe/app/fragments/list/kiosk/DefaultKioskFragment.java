package org.newnewpipe.app.fragments.list.kiosk;

import android.os.Bundle;

import org.newnewpipe.app.error.ErrorInfo;
import org.newnewpipe.app.error.UserAction;
import org.newnewpipe.extractor.NewPipe;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.kiosk.KioskList;
import org.newnewpipe.app.util.KioskTranslator;
import org.newnewpipe.app.util.ServiceHelper;

public class DefaultKioskFragment extends KioskFragment {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (serviceId < 0) {
            updateSelectedDefaultKiosk();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceId != ServiceHelper.getSelectedServiceId(requireContext())) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            updateSelectedDefaultKiosk();
            reloadContent();
        }
    }

    private void updateSelectedDefaultKiosk() {
        try {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext());

            final KioskList kioskList = NewPipe.getService(serviceId).getKioskList();
            kioskId = kioskList.getDefaultKioskId();
            url = kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).getUrl();

            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
            name = kioskTranslatedName;

            currentInfo = null;
            currentNextPage = null;
        } catch (final ExtractionException e) {
            showError(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"));
        }
    }
}
