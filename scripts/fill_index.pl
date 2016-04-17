use strict;
use warnings;
use utf8;
use feature 'say';

=info 

If this script doesn't work, please, read README file

=cut


use Mojo::UserAgent;
use Data::Dumper;
use XML::Simple;


my $ua = Mojo::UserAgent->new;
my $xs = XML::Simple->new;

my $n = $ARGV[0] || 56.103704;
my $s = $ARGV[1] || 55.225368;
my $e = $ARGV[2] || 38.106079;
my $w = $ARGV[3] || 37.117309;


# 54.831281&s=56.585708&e=38.609619&w=36.63208
my $body = "<bbox-query s=\"$s\" n=\"$n\" w=\"$w\" e=\"$e\"/>
         <query type=\"node\">
            <item/>
            <has-kv k=\"amenity\" v=\"cafe\"/>
            <has-kv k=\"cuisine\" v=\"coffee_shop\"/>
         </query>
         <print/>";

say $body;

my $xml = $ua->post("http://overpass-api.de/api/interpreter" => {Accept => '*/*'} => $body)->res->body;

say "found: ".scalar(keys %{$xs->XMLin($xml)->{node}});

foreach my $id (%{$xs->XMLin($xml)->{node}}) {
    #say $id;

    my $tags    = $xs->XMLin($xml)->{node}{$id}{tag};
    my $name    = get_tag('name:ru', @$tags) || get_tag('name', @$tags);
    my $lat     = $xs->XMLin($xml)->{node}{$id}{lat};
    my $lon     = $xs->XMLin($xml)->{node}{$id}{lon};
    my $hours   = get_tag('opening_hours', @$tags);

    if($name && $lat && $lon && $hours) {
        #say $name;
        #say $lat;
        #say $lon;

        my $json = "{
            \"name\": \"$name\",
            \"opening_hours\": \"$hours\",
            \"location\" : {
                \"lat\": $lat,
                \"lon\": $lon
            }
        }";

# say Dumper $json;

        my $tx = $ua->put("http://localhost:9200/map/coffee/$id" => {Accept => '*/*'} => $json);
        #say Dumper $tx->res->body;
        # last;
    }


}

sub get_tag {
    my ($name, @a) = @_;

    my @tag = grep { $_->{k} eq $name } @a;

    return $tag[0]->{v};
}