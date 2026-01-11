package banca;

import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import javax.swing.SwingUtilities;

public class GUIAgent extends Agent {

	private BankGUIUpdater guiUpdater;
	private AID exchangeAgentAID = null;
	private List<String> branches = new ArrayList<>();
	private boolean isRequestingRates = false;
	private volatile String pendingNotificationsReplyWith = null;
	private volatile String pendingNotificationsAccountId = null;
	private static final long NOTIFICATIONS_TIMEOUT_MS = 5000;

	protected void setup() {
		System.out.println(getLocalName() + " started.");

		try {
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setType("gui-agent");
			sd.setName("bt-gui-service");
			dfd.addServices(sd);
			DFService.register(this, dfd);
			System.out.println(getLocalName() + " registered as GUI agent.");
		} catch (Exception e) {
			System.err.println(getLocalName() + " failed to register with DF: " + e.getMessage());
		}

		SwingUtilities.invokeLater(() -> {
			BankGUI gui = new BankGUI(this);
			guiUpdater = gui;
		});

		addBehaviour(new ReceiveRepliesBehaviour());

		addBehaviour(new InitialDiscoveryBehaviour());
	}

	private class ReceiveRepliesBehaviour extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchConversationId("EXCHANGE_RATES_UPDATE");
			ACLMessage msg = receive(mt);

			if (msg != null) {
				if (guiUpdater != null) {
					guiUpdater.updateExchangeRates(msg.getContent());
				}
				isRequestingRates = false;
				return;
			}

			msg = receive();
			if (msg != null) {
				System.out.println("[" + getLocalName() + "] Received: " + msg.getConversationId() + " from "
						+ msg.getSender().getLocalName());

				if ("SYNC_ACCOUNT".equals(msg.getConversationId())) {
					return;
				}

				// NOTE: NOTIFICATIONS_LIST replies are consumed by RequestNotificationsBehaviour using
				// blockingReceive() with a correlation id, to avoid out-of-order/race issues.
				if ("NOTIFICATIONS_LIST".equals(msg.getConversationId())) {
					System.out.println("[" + getLocalName()
							+ "] Ignoring NOTIFICATIONS_LIST in ReceiveRepliesBehaviour (handled by request listener)");
					return;
				}

				if (guiUpdater != null) {
					guiUpdater.appendOutput("[" + msg.getSender().getLocalName() + "] " + msg.getContent());
				}
			} else {
				block();
			}
		}
	}

	private class InitialDiscoveryBehaviour extends Behaviour {
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// wait for GUI to initialize
				block(1000);
				step = 1;
				break;

			case 1:
				// look for branches
				addBehaviour(new DiscoverBranchesBehaviour(true));
				step = 2;
				break;

			case 2:
				// look for exchange agent
				addBehaviour(new DiscoverExchangeAgentBehaviour());
				step = 3;
				break;
			}
		}

		public boolean done() {
			return step >= 3;
		}

	}

	public void sendRequest(String branch, String action, String content) {
		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
		msg.addReceiver(new AID(branch, AID.ISLOCALNAME));
		msg.setConversationId(action);
		msg.setContent(content);
		send(msg);
	}

	public void requestExchangeRates() {
		if (isRequestingRates) {
			if (guiUpdater != null) {
				guiUpdater.updateExchangeRates("Already requesting rates...");
			}
			return;
		}

		if (exchangeAgentAID != null) {
			isRequestingRates = true;
			addBehaviour(new RequestRatesBehaviour());
		} else if (guiUpdater != null) {
			guiUpdater.updateExchangeRates("Exchange agent not found. Searching...");
			addBehaviour(new DiscoverExchangeAgentBehaviour());
		}
	}

	public void refreshBranches() {
		addBehaviour(new DiscoverBranchesBehaviour(false));
	}

	private class RequestRatesBehaviour extends OneShotBehaviour {
		public void action() {
			ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
			msg.addReceiver(exchangeAgentAID);
			msg.setConversationId("GET_EXCHANGE_RATES");
			msg.setContent("Request rates");
			send(msg);

			addBehaviour(new RateRequestTimeoutBehaviour());
		}
	}

	private class RateRequestTimeoutBehaviour extends Behaviour {
		private long startTime = System.currentTimeMillis();
		private final long TIMEOUT = 5000;

		public void action() {
			long elapsed = System.currentTimeMillis() - startTime;
			if (elapsed > TIMEOUT) {
				if (guiUpdater != null) {
					guiUpdater.updateExchangeRates("Rate request timed out");
				}
				isRequestingRates = false;
			}
			block(1000);
		}

		public boolean done() {
			return !isRequestingRates || (System.currentTimeMillis() - startTime > TIMEOUT);
		}
	}

	private class DiscoverBranchesBehaviour extends Behaviour {
		private int attempts = 0;
		private final int MAX_ATTEMPTS = 10;
		private boolean isInitial;

		public DiscoverBranchesBehaviour(boolean isInitial) {
			this.isInitial = isInitial;
		}

		public void action() {
			try {
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("bank-branch");
				template.addServices(sd);

				DFAgentDescription[] results = DFService.search(myAgent, template);

				List<String> foundBranches = new ArrayList<>();
				for (DFAgentDescription dfd : results) {
					foundBranches.add(dfd.getName().getLocalName());
				}

				branches = foundBranches;

				if (!foundBranches.isEmpty()) {
					System.out
							.println(getLocalName() + " found " + foundBranches.size() + " branches: " + foundBranches);
					if (guiUpdater != null) {
						guiUpdater.updateBranchesList(foundBranches);
					}
				} else {
					attempts++;
					if (!isInitial && guiUpdater != null) {
						guiUpdater.appendOutput("No branches found (attempt " + attempts + ")");
					}
				}

			} catch (Exception e) {
				attempts++;
				if (attempts == 1) {
					System.err.println(getLocalName() + " error searching branches: " + e.getMessage());
					if (guiUpdater != null) {
						guiUpdater.appendOutput("Error searching for branches: " + e.getMessage());
					}
				}
			}

			if (branches.isEmpty() && attempts < MAX_ATTEMPTS) {
				block(2000);
			}
		}

		public boolean done() {
			return !branches.isEmpty() || attempts >= MAX_ATTEMPTS;
		}
	}

	private class DiscoverExchangeAgentBehaviour extends Behaviour {
		private int attempts = 0;
		private final int MAX_ATTEMPTS = 10;

		public void action() {
			if (exchangeAgentAID != null) {
				return;
			}

			try {
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("currency-exchange");
				template.addServices(sd);

				DFAgentDescription[] results = DFService.search(myAgent, template);
				if (results.length > 0) {
					exchangeAgentAID = results[0].getName();
					System.out.println(getLocalName() + " found exchange agent: " + exchangeAgentAID.getLocalName());

					requestExchangeRates();

				} else {
					attempts++;
					if (guiUpdater != null && attempts <= 3) {
						guiUpdater.updateExchangeRates("Looking for exchange agent...");
					}
				}
			} catch (Exception e) {
				attempts++;
				if (guiUpdater != null && attempts == 1) {
					guiUpdater.updateExchangeRates("Error: " + e.getMessage());
				}
			}

			if (exchangeAgentAID == null && attempts < MAX_ATTEMPTS) {
				block(2000);
			}
		}

		public boolean done() {
			return exchangeAgentAID != null || attempts >= MAX_ATTEMPTS;
		}
	}

	public void requestNotifications(String accountId) {
		addBehaviour(new RequestNotificationsBehaviour(accountId));
	}

	public void setLowBalanceThreshold(String accountId, String threshold) {
		addBehaviour(new SetThresholdBehaviour(accountId, threshold));
	}

	public void clearNotifications(String accountId) {
		addBehaviour(new ClearNotificationsBehaviour(accountId));
	}

	private class RequestNotificationsBehaviour extends OneShotBehaviour {
		private String accountId;

		public RequestNotificationsBehaviour(String accountId) {
			this.accountId = accountId;
		}

		public void action() {
			try {
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("notification");
				template.addServices(sd);

				DFAgentDescription[] results = DFService.search(myAgent, template);

				if (results.length > 0) {
					AID notificationAgent = results[0].getName();
					ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
					msg.addReceiver(notificationAgent);
					msg.setConversationId("GET_NOTIFICATIONS");
					msg.setContent(accountId);

					String correlationId = "GET_NOTIFICATIONS-" + accountId + "-" + System.currentTimeMillis();
					msg.setReplyWith(correlationId);

					pendingNotificationsReplyWith = correlationId;
					pendingNotificationsAccountId = accountId;

					send(msg);

					MessageTemplate mt = MessageTemplate.and(
							MessageTemplate.MatchConversationId("NOTIFICATIONS_LIST"),
							MessageTemplate.MatchInReplyTo(correlationId));

					ACLMessage reply = blockingReceive(mt, NOTIFICATIONS_TIMEOUT_MS);
					if (reply != null) {
						pendingNotificationsReplyWith = null;
						pendingNotificationsAccountId = null;
						if (guiUpdater != null) {
							guiUpdater.updateNotificationArea(reply.getContent());
						}
					} else {
						pendingNotificationsReplyWith = null;
						pendingNotificationsAccountId = null;
						if (guiUpdater != null) {
							guiUpdater.updateNotificationArea(
									"Timed out loading notifications for " + accountId + ". Please try again.");
						}
					}
				} else {
					if (guiUpdater != null) {
						guiUpdater.showNotification("Notification agent not found");
					}
				}
			} catch (Exception e) {
				pendingNotificationsReplyWith = null;
				pendingNotificationsAccountId = null;
				if (guiUpdater != null) {
					guiUpdater.showNotification("Error: " + e.getMessage());
				}
			}
		}
	}

	private class SetThresholdBehaviour extends OneShotBehaviour {
		private String accountId;
		private String threshold;

		public SetThresholdBehaviour(String accountId, String threshold) {
			this.accountId = accountId;
			this.threshold = threshold;
		}

		public void action() {
			try {
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("notification");
				template.addServices(sd);

				DFAgentDescription[] results = DFService.search(myAgent, template);

				if (results.length > 0) {
					AID notificationAgent = results[0].getName();
					ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
					msg.addReceiver(notificationAgent);
					msg.setConversationId("SET_LOW_BALANCE_THRESHOLD");
					msg.setContent(accountId + ";" + threshold);
					send(msg);
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private class ClearNotificationsBehaviour extends OneShotBehaviour {
		private String accountId;

		public ClearNotificationsBehaviour(String accountId) {
			this.accountId = accountId;
		}

		public void action() {
			try {
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("notification");
				template.addServices(sd);

				DFAgentDescription[] results = DFService.search(myAgent, template);

				if (results.length > 0) {
					AID notificationAgent = results[0].getName();
					ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
					msg.addReceiver(notificationAgent);
					msg.setConversationId("CLEAR_NOTIFICATIONS");
					msg.setContent(accountId);
					send(msg);
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}
}