package aicrediflux.token.integration.credit;

public interface CreditAccountFacade {
    long getBalance(int userId);
    void credit(int userId, long amount, String bizNo);
}
