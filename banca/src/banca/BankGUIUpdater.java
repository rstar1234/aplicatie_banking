package banca;

import java.util.List;

public interface BankGUIUpdater {
    void updateBranchesList(List<String> branches);
    void updateExchangeRates(String ratesText);
    void appendOutput(String text);
	void showNotification(String notification);
	void updateNotificationArea(String text);
}