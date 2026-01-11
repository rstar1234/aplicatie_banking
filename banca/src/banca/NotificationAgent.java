package banca;

import jade.core.*;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.DFService;
import jade.lang.acl.ACLMessage;
import java.util.*;

public class NotificationAgent extends Agent {

	private Map<String, Double> accountLowBalanceThresholds = new HashMap<>();
	private Map<String, List<String>> accountNotifications = new HashMap<>();
	private static final double DEFAULT_LOW_BALANCE = 100.0;

	protected void setup() {
		System.out.println(getLocalName() + " started - Notification Service");

		registerWithDF();

		addBehaviour(new NotificationBehaviour());

		addBehaviour(new PreferenceBehaviour());
	}

	private void registerWithDF() {
		try {
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());

			ServiceDescription sd = new ServiceDescription();
			sd.setType("notification");
			sd.setName("bt-notification-service");
			dfd.addServices(sd);

			DFService.register(this, dfd);
			System.out.println(getLocalName() + " registered as notification service.");
		} catch (Exception e) {
			System.err.println(getLocalName() + " failed to register with DF: " + e.getMessage());
		}
	}

	private class NotificationBehaviour extends CyclicBehaviour {
		public void action() {
			ACLMessage msg = receive();
			if (msg != null) {
				if ("SYNC_ACCOUNT".equals(msg.getConversationId())) {
					String[] parts = msg.getContent().split(";");
					if (parts.length == 2) {
						String accountId = parts[0];
						double newBalance = Double.parseDouble(parts[1]);

						checkAndNotify(accountId, newBalance);
					}
				} else if ("TRANSACTION_COMPLETE".equals(msg.getConversationId())) {
					String transactionInfo = msg.getContent();
					// Content format (from BankBranchAgent):
					// accountId;transactionType;amount;oldBalance;newBalance
					String accountId = "system";
					try {
						String[] parts = transactionInfo.split(";", 2);
						if (parts.length >= 1 && parts[0] != null && !parts[0].trim().isEmpty()) {
							accountId = parts[0];
						}
					} catch (Exception ignore) {
						// fall back to system
					}
					String notification = "Transaction completed: " + transactionInfo;
					storeNotification(accountId, notification);
				} else if ("ACCOUNT_OPENED".equals(msg.getConversationId())) {
					String accountId = msg.getContent();
					String notification = "Account " + accountId + " opened successfully";
					storeNotification(accountId, notification);
				}
			} else {
				block();
			}
		}

		private void checkAndNotify(String accountId, double balance) {
			double threshold = accountLowBalanceThresholds.getOrDefault(accountId, DEFAULT_LOW_BALANCE);

			if (balance < threshold) {
				String notification = "Low balance alert for account " + accountId + ": "
						+ String.format("%.2f", balance) + " (threshold: " + threshold + ")";
				storeNotification(accountId, notification);

				sendNotificationToGUI("NOTIFICATION_ALERT", notification);
			}

			if (balance == 0.0) {
				String notification = "Account " + accountId + " has zero balance";
				storeNotification(accountId, notification);
				sendNotificationToGUI("NOTIFICATION_ALERT", notification);
			}
		}
	}

	private void sendNotificationToGUI(String type, String content) {
		try {
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("gui-agent");
			template.addServices(sd);

			DFAgentDescription[] results = DFService.search(this, template);

			if (results.length > 0) {
				AID guiAgent = results[0].getName();
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.addReceiver(guiAgent);
				msg.setConversationId(type);
				msg.setContent(content);
				send(msg);
			}
		} catch (Exception e) {
			// GUI not found yet, will try again next time
		}
	}

	private void storeNotification(String accountId, String notification) {
		accountNotifications.putIfAbsent(accountId, new ArrayList<>());
		List<String> notifications = accountNotifications.get(accountId);
		notifications.add(new Date() + ": " + notification);

		if (notifications.size() > 10) {
			notifications.remove(0);
		}

		System.out.println("[" + getLocalName() + "] " + notification);
	}

	private class PreferenceBehaviour extends CyclicBehaviour {
		public void action() {
			ACLMessage msg = receive();
			if (msg != null) {
				if ("SET_LOW_BALANCE_THRESHOLD".equals(msg.getConversationId())) {
					String[] parts = msg.getContent().split(";");
					if (parts.length == 2) {
						String accountId = parts[0];
						double threshold = Double.parseDouble(parts[1]);
						accountLowBalanceThresholds.put(accountId, threshold);

						String confirmation = "Set low balance threshold for " + accountId + ": " + threshold;
						storeNotification(accountId, confirmation);

						ACLMessage reply = msg.createReply();
						reply.setPerformative(ACLMessage.INFORM);
						reply.setConversationId("NOTIFICATION_INFO");
						reply.setContent(confirmation);
						send(reply);
					}
				} else if ("GET_NOTIFICATIONS".equals(msg.getConversationId())) {
					String accountId = msg.getContent();
					List<String> notifications = accountNotifications.getOrDefault(accountId, new ArrayList<>());

					ACLMessage reply = msg.createReply();
					reply.setPerformative(ACLMessage.INFORM);
					reply.setConversationId("NOTIFICATIONS_LIST");
					if (msg.getReplyWith() != null) {
						reply.setInReplyTo(msg.getReplyWith());
					}

					if (notifications.isEmpty()) {
						reply.setContent("No notifications for account " + accountId);
					} else {
						StringBuilder sb = new StringBuilder();
						sb.append("Notifications for ").append(accountId).append(":\n");
						for (String note : notifications) {
							sb.append("- ").append(note).append("\n");
						}
						reply.setContent(sb.toString());
					}
					send(reply);
				} else if ("CLEAR_NOTIFICATIONS".equals(msg.getConversationId())) {
					String accountId = msg.getContent();
					accountNotifications.remove(accountId);

					ACLMessage reply = msg.createReply();
					reply.setPerformative(ACLMessage.INFORM);
					reply.setConversationId("NOTIFICATION_INFO");
					reply.setContent("Cleared notifications for account " + accountId);
					send(reply);
				}
			} else {
				block();
			}
		}
	}
}