package pods.cabs;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.util.Random;

public class Cab {
    public enum CabState {
        AVAILABLE, COMMITTED,
        GIVING_RIDE, SIGNED_OUT
    }

    String cabId;
    /**
     * each cab maintains a reply id. All
     * the messages sent by this cab will contain
     * the unique monotonically increasing msgId.
      */
    long cabReplyId;

    /**
     *  If the cab is interested
     *  in taking the next ride request
     * */
    boolean isInterested;
    CabState state;
    long lastKnownLocation;
    long rideCnt;

    /**
     *
     * All following fields stores
     * information about the ongoing ride.
     * */

    long rideId;
    long sourceLocation;
    long destinationLocation;
    ActorRef<FulfillRide.Command> fulFillRideActorRef;

    interface CabCommand {}

    public static final class RequestRide implements CabCommand {
        long rideId;
        long sourceLoc;
        long destinationLoc;
        ActorRef<FulfillRide.Command> replyTo;

        public RequestRide(long rideId, long sourceLoc, long destinationLoc,
                           ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    public static final class RideStarted implements CabCommand {
        public RideStarted() {
        }
    }

    public static final class RideCanceled implements CabCommand {
        public RideCanceled() {
        }
    }

    public final static class RideEnded implements CabCommand {
        public RideEnded() {
        }
    }

    public static final class SignIn implements CabCommand {
        long initialPos;

        public SignIn(long initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOut implements CabCommand {
        public SignOut() {
        }
    }

    public static final class NumRides implements CabCommand {
        ActorRef<NumRideResponse> replyTo;
        public NumRides(ActorRef<NumRideResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements CabCommand {
        ActorRef<NumRideResponse> replyTo;
        public Reset(ActorRef<NumRideResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    interface CabResponse {}

    public static final class NumRideResponse implements CabResponse{
        long response;
        long cabReplyId;

        public NumRideResponse(long response, long cabReplyId) {
            this.response = response;
            this.cabReplyId = cabReplyId;
        }
    }

    public static final class RequestRideResponse implements FulfillRide.Command {
        boolean response;
        long cabReplyId;

        public RequestRideResponse(boolean response, long cabReplyId) {
            this.response = response;
            this.cabReplyId = cabReplyId;
        }
    }

    public Cab(String cabId) {
        this.cabId = cabId;
        this.cabReplyId = 0;
    }

    public Behavior<Cab.CabCommand> cab(){
        return Behaviors.receive(CabCommand.class)
                .onMessage(RequestRide.class, this::onRequestRide)
                .onMessage(RideStarted.class, this::onRideStarted)
                .onMessage(RideCanceled.class, this::onRideCanceled)
                .onMessage(RideEnded.class, this::onRideEnded)
                .onMessage(SignIn.class, this::onSignIn)
                .onMessage(SignOut.class, this::onSignOut)
                .onMessage(Reset.class, this::onReset)
                .onMessage(NumRides.class, this::onNumRides)
                .build();
    }

    public static Behavior<Cab.CabCommand> create(String cabId){
        return Behaviors.setup(
                ctx -> new Cab(cabId).cab());
    }

    public Behavior<Cab.CabCommand> onRideStarted(RideStarted rideStarted){
        state = CabState.GIVING_RIDE;
        return cab();
    }

    public Behavior<Cab.CabCommand> onRideCanceled(RideCanceled rideCanceled){
        state = CabState.AVAILABLE;
        return cab();
    }

    public Behavior<Cab.CabCommand> onRideEnded(RideEnded rideEnded){
        state = CabState.AVAILABLE;
        lastKnownLocation = destinationLocation;
        rideCnt += 1;
        fulFillRideActorRef.tell(new FulfillRide.RideEnded(cabReplyId++));
        return cab();
    }

    public Behavior<Cab.CabCommand> onRequestRide(RequestRide requestRide){
        if(state!=CabState.AVAILABLE){
            requestRide.replyTo.tell(new RequestRideResponse(false, cabReplyId++));
            return cab();
        }

        if(isInterested){
            isInterested = false;
            state = CabState.COMMITTED;
            sourceLocation = requestRide.sourceLoc;
            destinationLocation = requestRide.destinationLoc;
            rideId = requestRide.rideId;
            fulFillRideActorRef = requestRide.replyTo;
            requestRide.replyTo.tell(new RequestRideResponse(true, cabReplyId++));
        }else{
            isInterested = true;
            requestRide.replyTo.tell(new RequestRideResponse(false, cabReplyId++));
        }

        return cab();
    }

    public Behavior<Cab.CabCommand> onSignIn(SignIn signIn){
        state = CabState.AVAILABLE;
        lastKnownLocation = signIn.initialPos;
        rideCnt = 0;
        isInterested = true;
        Random random = new Random();
        Globals.rideServiceRefs.get(random.nextInt(Globals.rideServiceRefs.size())).tell(
                new RideService.CabSignsIn(cabId, signIn.initialPos, cabReplyId++)
        );
        return cab();
    }

    public Behavior<Cab.CabCommand> onSignOut(SignOut signOut){
        state = CabState.SIGNED_OUT;
        Random random = new Random();
        Globals.rideServiceRefs.get(random.nextInt(Globals.rideServiceRefs.size())).tell(
                new RideService.CabSignsOut(cabId, cabReplyId++)
        );
        return cab();
    }

    public Behavior<Cab.CabCommand> onReset(Reset reset){
        reset.replyTo.tell(new NumRideResponse(rideCnt, cabReplyId++));
        if(state == CabState.GIVING_RIDE)
            onRideEnded(new RideEnded());
        if(state != CabState.SIGNED_OUT)
            onSignOut(new SignOut());
        return cab();
    }

    public Behavior<Cab.CabCommand> onNumRides(NumRides numRides){
        numRides.replyTo.tell(new NumRideResponse(rideCnt, cabReplyId++));
        return cab();
    }

    //TODO: change the return values of handlers so that only
    // legal messages gets accepted in each state.

}