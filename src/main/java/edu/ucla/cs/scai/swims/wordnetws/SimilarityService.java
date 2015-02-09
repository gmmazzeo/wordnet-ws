/* 
 * Copyright 2015 ScAi, CSD, UCLA.
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

package edu.ucla.cs.scai.swims.wordnetws;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import slib.utils.ex.SLIB_Ex_Critic;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
@Path("/similarity")
public class SimilarityService {

    @GET
    //@Consumes(MediaType.APPLICATION_JSON)
    //@Produces(MediaType.APPLICATION_JSON)
    @Path("/{param1}/{param2}")
    public Response similarity(@PathParam("param1") String w1,
            @PathParam("param2") String w2) {
        Double res=0d;
        try {
            String [] s1=w1.split(",");
            String [] s2=w2.split(",");
            if (s1.length==1 && s2.length==1) {
                res=WordnetService.getInstance().pairSimilarity(s1[0], s2[0]);
            } else {
                res = WordnetService.getInstance().jaccardSimilarity(s1, s2);
            }
        } catch (SLIB_Ex_Critic ex) {
            ex.printStackTrace();
        }
        return Response.status(200).entity(res.toString()).build();
    }
}
