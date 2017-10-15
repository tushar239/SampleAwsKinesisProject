package utils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

/**
 * Provides utilities for retrieving credentials to talk to AWS
 */
public class CredentialUtils {
    public static AWSCredentialsProvider getCredentialsProvider() throws Exception {
        /*
         The ProfileCredentialsProvider will return your [Tushar Chokshi] credential profile by reading from the credentials file located at (~/.aws/credentials).
         It has
         [Tushar Chokshi]
         aws_access_key_id=<.....>
         aws_secret_access_key=<.....>
         */
        AWSCredentialsProvider credentialsProvider = null;
        try {
            credentialsProvider = new ProfileCredentialsProvider("Tushar Chokshi");
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        return credentialsProvider;
    }
}
