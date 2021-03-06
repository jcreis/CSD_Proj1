package api;


import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Path("/wallet")
public class LoaderResources {

    private Map<String, Process> processes = new ConcurrentHashMap<>();

    public LoaderResources(){

    }


    @POST
    @Path("/launch")
    public void launch( @QueryParam("port") String port,
                        @QueryParam("id")String id) throws IOException {


        ProcessBuilder pb = new ProcessBuilder("java", "-cp", "target/lib/jersey-media-json-jackson-2.25.1.jar:target/rest-0.0.1-SNAPSHOT-jar-with-dependencies.jar", "rest.server.WalletServer", port, id);

        Process p = pb.start();
        processes.put(id, p);


    }

    @POST
    @Path("/stop")
    public void stop(@QueryParam("id")String id) {

        if(processes.get(id).isAlive()) {
            processes.get(id).destroy();
        }else{
            System.out.println("Process is not alive");
        }


    }


}







