package banca;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class BankBranchAgent extends Agent{
    
    private Map<String, Double> accounts = new HashMap<>();
    
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        registerToDF();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    handleMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    
    private void registerToDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("bank-branch");
        sd.setName("bt-bank-branch-service");

        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered to DF.");
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }
    
    private List<AID> findOtherBranches() {
        List<AID> agents = new ArrayList<>();

        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("bank-branch");
        template.addServices(sd);

        try {
            DFAgentDescription[] results =
                    DFService.search(this, template);

            for (DFAgentDescription dfd : results) {
                AID aid = dfd.getName();
                if (!aid.equals(getAID())) {
                    agents.add(aid);
                }
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return agents;
    }

    
    private void handleMessage(ACLMessage msg) {
        String cid = msg.getConversationId();
        String content = msg.getContent();

        switch (cid) {
        case "OPEN_ACCOUNT":
            if (accounts.containsKey(content)) {
                reply(msg, "Account " + content + " already exists");
                break;
            }
            
            accounts.put(content, 0.0);
            syncAccount(content, 0.0);
            sendAccountOpenedNotification(content);
            reply(msg, "Account " + content + " opened with balance 0.0");
            break;

        case "DEPOSIT":
            String[] d = content.split(";");
            String acc = d[0];
            double amount_d = Double.parseDouble(d[1]);

            if (!accounts.containsKey(acc)) {
                reply(msg, "Account " + acc + " doesn't exist. Open account first.");
                break;
            }
            
            if (amount_d <= 0) {
                reply(msg, "Deposit amount must be positive");
                break;
            }
            
            double oldBalanceDeposit = accounts.get(acc);
            accounts.put(acc, oldBalanceDeposit + amount_d);
            syncAccount(acc, accounts.get(acc));
            
            sendTransactionNotification(acc, "DEPOSIT", amount_d, oldBalanceDeposit, accounts.get(acc));
            
            reply(msg, "Deposit successful. New balance: " + accounts.get(acc));
            break;
                    
        case "WITHDRAW":
            String[] w = content.split(";");
            String account = w[0];
            double amount_w = Double.parseDouble(w[1]);
            
            if (!accounts.containsKey(account)) {
                reply(msg, "Account " + account + " doesn't exist. Open account first.");
                break;
            }
            
            if (amount_w <= 0) {
                reply(msg, "Withdraw amount must be positive");
                break;
            }
            
            double balance = accounts.get(account);
            
            if (balance < amount_w) {
                reply(msg, "Insufficient funds. Balance: " + balance);
                break;
            }
            
            double oldBalanceWithdraw = balance;
            accounts.put(account, balance - amount_w);
            syncAccount(account, accounts.get(account));
            
            sendTransactionNotification(account, "WITHDRAW", amount_w, oldBalanceWithdraw, accounts.get(account));
            
            reply(msg, "Withdraw successful. New balance: " + accounts.get(account));
            break;

            case "SYNC_ACCOUNT":
                String[] s = content.split(";");
                String accountId = s[0];
                double newBalance = Double.parseDouble(s[1]);
                accounts.put(accountId, newBalance);
                System.out.println(getLocalName() + " synced account " + accountId + " = " + newBalance);
                break;
        }
    }

    private void syncAccount(String accountId, double balance) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setConversationId("SYNC_ACCOUNT");
        msg.setContent(accountId + ";" + balance);

        List<AID> otherBranches = findOtherBranches();
        for (AID aid : otherBranches) {
            msg.addReceiver(aid);
        }
        
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("notification");
            template.addServices(sd);

            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription dfd : results) {
                AID aid = dfd.getName();
                msg.addReceiver(aid);
            }
        } catch (Exception e) {
            //notification agent might not be registered yet
        }
        
        send(msg);
    }

    private void reply(ACLMessage msg, String text) {
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setContent(text);
        send(reply);
    }
    
    private void sendTransactionNotification(String accountId, String transactionType, 
                                            double amount, double oldBalance, double newBalance) {
        ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
        notification.setConversationId("TRANSACTION_COMPLETE");
        notification.setContent(accountId + ";" + transactionType + ";" + 
                              amount + ";" + oldBalance + ";" + newBalance);
        
        List<AID> otherAgents = findOtherAgents("notification");
        for (AID aid : otherAgents) {
            notification.addReceiver(aid);
        }
        
        if (!otherAgents.isEmpty()) {
            send(notification);
        }
    }

    private List<AID> findOtherAgents(String agentType) {
        List<AID> agents = new ArrayList<>();
        
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(agentType);
            template.addServices(sd);

            DFAgentDescription[] results = DFService.search(this, template);
            
            for (DFAgentDescription dfd : results) {
                AID aid = dfd.getName();
                if (!aid.equals(getAID())) {
                    agents.add(aid);
                }
            }
        } catch (Exception e) {
            // ignore - agent might not exist yet
        }
        
        return agents;
    }

    
    private void sendAccountOpenedNotification(String accountId) {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("notification");
            template.addServices(sd);

            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription dfd : results) {
                ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
                notification.addReceiver(dfd.getName());
                notification.setConversationId("ACCOUNT_OPENED");
                notification.setContent(accountId);
                send(notification);
            }
        } catch (Exception e) {
            //notification agent might not be registered yet
        }
    }
}